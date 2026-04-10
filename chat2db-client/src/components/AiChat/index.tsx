import React, { memo, useState, useRef, useEffect, useCallback } from 'react';
import { Button, Input, Spin, message, Tag, Alert } from 'antd';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { v4 as uuidv4 } from 'uuid';
import { formatParams } from '@/utils/url';
import connectToEventSource, { cancelChatSession } from '@/utils/eventSource';
import CascaderDB from '@/components/CascaderDB';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import {
  useAiChatStore,
  ChatStateType,
  IChatMessage,
} from '@/pages/main/workspace/store/aiChatStore';
import styles from './index.less';

const STATE_LABELS: Record<ChatStateType, { text: string; color: string }> = {
  IDLE: { text: '等待中', color: 'default' },
  AUTO_SELECTING_TABLES: { text: '选择表...', color: 'processing' },
  FETCHING_TABLE_SCHEMA: { text: '获取表结构...', color: 'processing' },
  BUILDING_PROMPT: { text: '构建提示...', color: 'processing' },
  STREAMING: { text: 'AI生成中', color: 'processing' },
  COMPLETED: { text: '完成', color: 'success' },
  FAILED: { text: '失败', color: 'error' },
};

const isActiveState = (state?: ChatStateType): boolean => {
  return state
    ? ['AUTO_SELECTING_TABLES', 'FETCHING_TABLE_SCHEMA', 'BUILDING_PROMPT', 'STREAMING'].includes(
        state,
      )
    : false;
};

interface IProps {
  className?: string;
  data?: any;
}

export default memo<IProps>(() => {
  const [inputValue, setInputValue] = useState('');
  const closeEventSource = useRef<() => void>();
  const sessionIdRef = useRef<string>();

  const {
    currentSessionId,
    sessions,
    createSession,
    updateState,
    appendContent,
    addMessage,
    setSelectedTables,
    setSchemaInfo,
    setError,
    setLastRequest,
    clearSession,
    resetCurrentContent,
    lastRequest,
  } = useAiChatStore();

  const currentSession = currentSessionId ? sessions.get(currentSessionId) : null;

  console.log('[AiChat] currentSessionId:', currentSessionId);
  console.log('[AiChat] sessions:', sessions);
  console.log('[AiChat] currentSession:', currentSession);

  const { currentWorkspaceGlobalExtend, currentConnectionDetails } = useWorkspaceStore((state) => ({
    currentWorkspaceGlobalExtend: state.currentWorkspaceGlobalExtend,
    currentConnectionDetails: state.currentConnectionDetails,
  }));

  const [boundInfo, setBoundInfo] = useState({
    dataSourceId: currentConnectionDetails?.id,
    databaseName: currentWorkspaceGlobalExtend?.uniqueData?.databaseName || '',
    schemaName: currentWorkspaceGlobalExtend?.uniqueData?.schemaName || '',
  });

  useEffect(() => {
    setBoundInfo((prev) => ({
      dataSourceId: prev.dataSourceId || currentConnectionDetails?.id,
      databaseName: prev.databaseName || currentWorkspaceGlobalExtend?.uniqueData?.databaseName || '',
      schemaName: prev.schemaName || currentWorkspaceGlobalExtend?.uniqueData?.schemaName || '',
    }));
  }, [currentWorkspaceGlobalExtend, currentConnectionDetails]);

  const sendAiChat = useCallback(
    (messageText: string, promptType: string = 'NL_2_SQL') => {
      console.log('[AiChat] sendAiChat called with:', { messageText, promptType, boundInfo });
      if (!messageText.trim()) {
        message.warning('请输入问题');
        return;
      }

      if (!boundInfo.dataSourceId) {
        message.warning('请先选择数据库连接');
        return;
      }

      const sessionId = uuidv4();
      console.log('[AiChat] Created sessionId:', sessionId);
      sessionIdRef.current = sessionId;
      createSession(sessionId);

      const userMessage: IChatMessage = {
        id: uuidv4(),
        role: 'user',
        content: messageText,
      };
      addMessage(sessionId, userMessage);

      setLastRequest({
        message: messageText,
        promptType,
        dataSourceId: boundInfo.dataSourceId,
        databaseName: boundInfo.databaseName,
        schemaName: boundInfo.schemaName,
      });

      resetCurrentContent(sessionId);

      const params = formatParams({
        message: messageText,
        promptType,
        dataSourceId: boundInfo.dataSourceId,
        databaseName: boundInfo.databaseName,
        schemaName: boundInfo.schemaName,
        tableNames: null,
      });

      closeEventSource.current = connectToEventSource({
        url: `/api/ai/chat?${params}`,
        uid: sessionId,
        onOpen: () => {
          console.log('[AiChat] SSE connection opened');
          updateState(sessionId, 'IDLE');
        },
        onStateChange: (state, _msg) => {
          console.log('[AiChat] State changed:', state, _msg);
          updateState(sessionId, state);
        },
        onMessage: (content) => {
          console.log('[AiChat] Message content received:', content);
          appendContent(sessionId, content);
        },
        onTablesSelected: (tables) => {
          console.log('[AiChat] Tables selected:', tables);
          setSelectedTables(sessionId, tables);
        },
        onSchemaFetched: (ddl) => {
          console.log('[AiChat] Schema fetched, ddl length:', ddl?.length);
          setSchemaInfo(sessionId, ddl);
        },
        onDone: () => {
          console.log('[AiChat] onDone callback, sessionId:', sessionId);
          updateState(sessionId, 'COMPLETED');
          const currentSessions = useAiChatStore.getState().sessions;
          console.log('[AiChat] sessions in onDone:', currentSessions);
          const session = currentSessions.get(sessionId);
          console.log('[AiChat] session in onDone:', session);
          if (session?.currentContent) {
            addMessage(sessionId, {
              id: uuidv4(),
              role: 'assistant',
              content: session.currentContent,
            });
          }
          closeEventSource.current = undefined;
        },
        onError: (error) => {
          console.error('[AiChat] SSE error:', error);
          setError(sessionId, error);
          message.error(error);
          closeEventSource.current = undefined;
        },
      });
    },
    [
      boundInfo,
      createSession,
      addMessage,
      setLastRequest,
      resetCurrentContent,
      updateState,
      appendContent,
      setSelectedTables,
      setSchemaInfo,
      sessions,
      setError,
    ],
  );

  const handleCancel = async () => {
    if (closeEventSource.current) {
      closeEventSource.current();
      closeEventSource.current = undefined;
    }
    if (sessionIdRef.current) {
      await cancelChatSession(sessionIdRef.current);
      clearSession(sessionIdRef.current);
    }
  };

  const handleRetry = () => {
    if (lastRequest) {
      sendAiChat(lastRequest.message, lastRequest.promptType);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendAiChat(inputValue);
    }
  };

  const handleBoundInfoChange = (value: {
    dataSourceId: number;
    databaseName: string;
    schemaName: string;
  }) => {
    setBoundInfo({
      dataSourceId: value.dataSourceId,
      databaseName: value.databaseName,
      schemaName: value.schemaName,
    });
  };

  const stateInfo = currentSession ? STATE_LABELS[currentSession.state] : null;
  const isProcessing = isActiveState(currentSession?.state);
  const isInputDisabled = isProcessing || !boundInfo.dataSourceId;

  return (
    <div className={styles.aiChatContainer}>
      {stateInfo && (
        <div className={styles.statusBar}>
          <Tag color={stateInfo.color}>{stateInfo.text}</Tag>
          {currentSession?.selectedTables && currentSession.selectedTables.length > 0 && (
            <div className={styles.selectedTables}>
              <span>已选择表：</span>
              {currentSession.selectedTables.map((t) => (
                <Tag key={t} color="blue">
                  {t}
                </Tag>
              ))}
            </div>
          )}
          {isProcessing && (
            <Button size="small" danger onClick={handleCancel}>
              停止
            </Button>
          )}
        </div>
      )}

      <div className={styles.contentArea}>
        {currentSession?.messages.map((msg) => (
          <div
            key={msg.id}
            className={msg.role === 'user' ? styles.userBlock : styles.aiBlock}
          >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
          </div>
        ))}
        {currentSession?.state === 'STREAMING' && currentSession.currentContent && (
          <div className={styles.aiBlock}>
            <Spin size="small">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {currentSession.currentContent}
              </ReactMarkdown>
            </Spin>
          </div>
        )}
        {currentSession?.state === 'FAILED' && currentSession.error && (
          <div className={styles.errorBlock}>
            <Alert type="error" message={currentSession.error} />
            <Button type="primary" onClick={handleRetry} style={{ marginTop: 8 }}>
              重试
            </Button>
          </div>
        )}
      </div>

      <div className={styles.inputFormArea}>
        <CascaderDB
          onChange={handleBoundInfoChange}
          curConnectionId={boundInfo.dataSourceId}
          curDatabaseName={boundInfo.databaseName}
          curSchemaName={boundInfo.schemaName}
        />
        <Input.TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="请输入您的问题..."
          autoSize={{ minRows: 3, maxRows: 6 }}
          disabled={isProcessing}
        />
        <div className={styles.buttonGroup}>
          <Button
            type="primary"
            onClick={() => sendAiChat(inputValue)}
            disabled={!inputValue.trim() || isInputDisabled}
          >
            发送
          </Button>
        </div>
      </div>
    </div>
  );
});
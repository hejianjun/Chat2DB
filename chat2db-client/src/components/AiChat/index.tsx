import React, { memo, useState, useRef, useEffect, useCallback } from 'react';
import { Button, Mentions, Spin, message, Tag, Alert, Modal, Select, Upload } from 'antd';
import {
  DownOutlined,
  RightOutlined,
  PlayCircleOutlined,
  SendOutlined,
  PlusOutlined,
  LinkOutlined,
  DeleteOutlined,
  PaperClipOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import hljs from 'highlight.js/lib/core';
import javascript from 'highlight.js/lib/languages/javascript';
import sqlLanguage from 'highlight.js/lib/languages/sql';
import ReactMarkdown, { Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { v4 as uuidv4 } from 'uuid';
import { formatParams } from '@/utils/url';
import connectToEventSource, { cancelChatSession, createChatPayload } from '@/utils/eventSource';
import CascaderDB from '@/components/CascaderDB';
import aiConversationService from '@/service/aiConversation';
import sqlService from '@/service/sql';
import {
  IAiChatPromptType,
  ITableCommentResult,
  IBatchTableCommentResult,
  IRedisKeyPrefixCommentResult,
  IFieldMappingResult,
  IDataExpressionResult,
  isConversationalSqlPromptType,
  isSimpleTaskPromptType,
} from '@/pages/main/workspace/store/common';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import {
  useAiChatStore,
  ChatStateType,
  IChatMessage,
  IChatImageAttachment,
} from '@/pages/main/workspace/store/aiChatStore';
import { getConsoleEditor } from '@/pages/main/workspace/components/SQLExecute/consoleEditorRegistry';
import { getSearchResult } from '@/pages/main/workspace/components/SQLExecute/searchResultRegistry';
import ConversationSidebar from './ConversationSidebar';
import styles from './index.less';

hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('sql', sqlLanguage);
hljs.registerAliases(['js', 'mql', 'mongo', 'mongodb'], { languageName: 'javascript' });

const STATE_LABELS: Record<ChatStateType, { text: string; color: string }> = {
  IDLE: { text: '等待中', color: 'default' },
  AUTO_SELECTING_TABLES: { text: '选择表...', color: 'processing' },
  FETCHING_TABLE_SCHEMA: { text: '获取表结构...', color: 'processing' },
  EXECUTING_EXPLAIN: { text: '分析执行计划...', color: 'processing' },
  BUILDING_PROMPT: { text: '构建提示...', color: 'processing' },
  STREAMING: { text: 'AI生成中', color: 'processing' },
  COMPLETED: { text: '完成', color: 'success' },
  FAILED: { text: '失败', color: 'error' },
};

const isActiveState = (state?: ChatStateType): boolean => {
  return state
    ? ['AUTO_SELECTING_TABLES', 'FETCHING_TABLE_SCHEMA', 'EXECUTING_EXPLAIN', 'BUILDING_PROMPT', 'STREAMING'].includes(state)
    : false;
};

interface ExplainPanelProps {
  explainResult: { sql: string; plan: string[][]; formatted: string; success: boolean };
}

const ExplainPanel = memo<ExplainPanelProps>(({ explainResult }) => {
  const [collapsed, setCollapsed] = useState(true);

  if (!explainResult || !explainResult.success || !explainResult.plan.length) {
    return null;
  }

  const headers = explainResult.plan[0];
  const rows = explainResult.plan.slice(1);

  return (
    <div className={styles.explainPanel}>
      <div className={styles.explainHeader} onClick={() => setCollapsed(!collapsed)}>
        {collapsed ? <RightOutlined /> : <DownOutlined />}
        <span>SQL 执行计划 (EXPLAIN)</span>
      </div>
      {!collapsed && (
        <div className={styles.explainContent}>
          <div className={styles.explainSql}>
            <code>{explainResult.sql}</code>
          </div>
          <div className={styles.explainTable}>
            <table className={styles.explainTableInner}>
              <thead>
                <tr>
                  {headers.map((h, i) => (
                    <th key={i}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row, ri) => (
                  <tr key={ri}>
                    {row.map((cell, ci) => (
                      <td key={ci}>{cell}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
});

function extractJsonFromContent(content: string): ITableCommentResult | null {
  try {
    const jsonMatch = content.match(/\{[\s\S]*"table_comment"[\s\S]*"column_comments"[\s\S]*\}/);
    if (jsonMatch) {
      return JSON.parse(jsonMatch[0]) as ITableCommentResult;
    }
    const directJson = JSON.parse(content);
    if (directJson.table_comment) {
      return directJson as ITableCommentResult;
    }
  } catch (e) {
    console.error('[extractJsonFromContent] Parse error:', e);
  }
  return null;
}

function extractBatchJsonFromContent(content: string): IBatchTableCommentResult | null {
  try {
    const jsonMatch = content.match(/\{[\s\S]*"tables"[\s\S]*\}/);
    if (jsonMatch) {
      return JSON.parse(jsonMatch[0]) as IBatchTableCommentResult;
    }
    const directJson = JSON.parse(content);
    if (directJson.tables) {
      return directJson as IBatchTableCommentResult;
    }
  } catch (e) {
    console.error('[extractBatchJsonFromContent] Parse error:', e);
  }
  return null;
}

function extractRedisKeyPrefixCommentFromContent(content: string): IRedisKeyPrefixCommentResult | null {
  try {
    const jsonMatch = content.match(/\{[\s\S]*"prefixes"[\s\S]*\}/);
    if (jsonMatch) {
      return JSON.parse(jsonMatch[0]) as IRedisKeyPrefixCommentResult;
    }
    const directJson = JSON.parse(content);
    if (directJson.prefixes) {
      return directJson as IRedisKeyPrefixCommentResult;
    }
  } catch (e) {
    console.error('[extractRedisKeyPrefixCommentFromContent] Parse error:', e);
  }
  return null;
}

function extractFieldMappingFromContent(content: string): IFieldMappingResult | null {
  try {
    const jsonMatch = content.match(/\{[\s\S]*"mappings"[\s\S]*\}/);
    if (jsonMatch) {
      return JSON.parse(jsonMatch[0]) as IFieldMappingResult;
    }
    const directJson = JSON.parse(content);
    if (directJson.mappings) {
      return directJson as IFieldMappingResult;
    }
  } catch (e) {
    console.error('[extractFieldMappingFromContent] Parse error:', e);
  }
  return null;
}

function extractDataExpressionFromContent(content: string): IDataExpressionResult | null {
  try {
    const jsonMatch = content.match(/\{[\s\S]*"column_expressions"[\s\S]*\}/);
    if (jsonMatch) {
      return JSON.parse(jsonMatch[0]) as IDataExpressionResult;
    }
    const directJson = JSON.parse(content);
    if (directJson.column_expressions) {
      return directJson as IDataExpressionResult;
    }
  } catch (e) {
    console.error('[extractDataExpressionFromContent] Parse error:', e);
  }
  return null;
}

type DatabaseStatementType = 'SQL' | 'MQL';

interface IDatabaseStatement {
  statement: string;
  type: DatabaseStatementType;
}

const MQL_LANGUAGE_NAMES = ['mql', 'mongo', 'mongodb', 'javascript', 'js'];
const SQL_LANGUAGE_NAMES = ['sql'];

const isMqlStatement = (statement: string): boolean => {
  return /\bdb(?:\.[A-Za-z_$][\w$]*|\[['"][^'"]+['"]\])+\s*\(/.test(statement);
};

const isSqlStatement = (statement: string): boolean => {
  return /\b(select|insert|update|delete|create|alter|drop|with|merge)\b/i.test(statement);
};

const extractStandaloneDatabaseStatement = (content: string): IDatabaseStatement | null => {
  const statement = content.trim();
  if (/^db(?:\.[A-Za-z_$][\w$]*|\[['"][^'"]+['"]\])+\s*\(/.test(statement)) {
    return { statement, type: 'MQL' };
  }
  if (/^(select|insert|update|delete|create|alter|drop|with|merge)\b/i.test(statement)) {
    return { statement, type: 'SQL' };
  }
  return null;
};

function extractDatabaseStatement(content: string): IDatabaseStatement | null {
  try {
    const fencedCodePattern = /```([^\r\n`]*)\r?\n?([\s\S]*?)```/g;
    let fencedCodeMatch = fencedCodePattern.exec(content);

    while (fencedCodeMatch) {
      const language = fencedCodeMatch[1]
        .trim()
        .toLowerCase()
        .split(/\s+/)[0];
      const statement = fencedCodeMatch[2].trim();

      if (statement && (MQL_LANGUAGE_NAMES.includes(language) || isMqlStatement(statement))) {
        return { statement, type: 'MQL' };
      }
      if (statement && (SQL_LANGUAGE_NAMES.includes(language) || isSqlStatement(statement))) {
        return { statement, type: 'SQL' };
      }

      fencedCodeMatch = fencedCodePattern.exec(content);
    }

    return extractStandaloneDatabaseStatement(content);
  } catch (e) {
    console.error('[extractDatabaseStatement] Parse error:', e);
  }
  return null;
}

const normalizeDatabaseMarkdown = (content: string): string => {
  if (content.includes('```')) {
    return content;
  }
  const databaseStatement = extractStandaloneDatabaseStatement(content);
  if (!databaseStatement) {
    return content;
  }
  return `\`\`\`${databaseStatement.type.toLowerCase()}\n${databaseStatement.statement}\n\`\`\``;
};

const MarkdownCode: NonNullable<Components['code']> = ({ inline, className, children, node: _node, ...props }) => {
  void _node;
  const code = String(children).replace(/\n$/, '');
  if (inline) {
    return (
      <code className={className} {...props}>
        {children}
      </code>
    );
  }

  const language = className?.match(/language-([\w-]+)/)?.[1]?.toLowerCase();
  const highlightLanguage =
    language && MQL_LANGUAGE_NAMES.includes(language)
      ? 'javascript'
      : language && SQL_LANGUAGE_NAMES.includes(language)
      ? 'sql'
      : isMqlStatement(code)
      ? 'javascript'
      : isSqlStatement(code)
      ? 'sql'
      : null;

  if (!highlightLanguage) {
    return (
      <code className={className} {...props}>
        {children}
      </code>
    );
  }

  const highlightedCode = hljs.highlight(code, { language: highlightLanguage }).value;
  return (
    <code
      className={[className, 'hljs'].filter(Boolean).join(' ')}
      dangerouslySetInnerHTML={{ __html: highlightedCode }}
      {...props}
    />
  );
};

const MARKDOWN_COMPONENTS: Components = {
  code: MarkdownCode,
};

const MAX_DIRECT_SSE_URL_LENGTH = 1800;
const MAX_IMAGE_COUNT = 4;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const SUPPORTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

const normalizeNullableText = (value?: string | null): string => value || '';

const isSameDataSource = (left?: number | null, right?: number | null): boolean => {
  return String(left ?? '') === String(right ?? '');
};

const getDefaultSessionTitle = (promptType: IAiChatPromptType, messageText: string): string => {
  // 简单任务是从其他页面跳转进来的，使用实际任务描述作为标题，避免沿用上一个会话的标题。
  if (isSimpleTaskPromptType(promptType)) {
    const title = messageText.trim().replace(/\s+/g, ' ');
    return title || promptType;
  }
  return promptType;
};

const DatabaseStatementActionButtons = memo<{
  statement: string;
  type: DatabaseStatementType;
  onExecute?: (statement: string, type: DatabaseStatementType) => void;
  onSendToEditor?: (statement: string, type: DatabaseStatementType) => void;
}>(({ statement, type, onExecute, onSendToEditor }) => {
  return (
    <div className={styles.sqlActionButtons}>
      <Button
        type="primary"
        icon={<PlayCircleOutlined />}
        onClick={() => onExecute?.(statement, type)}
        size="small"
      >
        执行{type}
      </Button>
      <Button
        icon={<SendOutlined />}
        onClick={() => onSendToEditor?.(statement, type)}
        size="small"
      >
        发送到编辑器
      </Button>
    </div>
  );
});

interface IProps {
  className?: string;
  data?: any;
}

export default memo<IProps>(() => {
  const [inputValue, setInputValue] = useState('');
  const closeEventSource = useRef<() => void>();
  const closeTitleEventSource = useRef<() => void>();
  const sessionIdRef = useRef<string>();
  const commentCallbackRef = useRef<(result: ITableCommentResult) => void>();
  const batchCommentCallbackRef = useRef<(result: IBatchTableCommentResult) => void>();
  const redisKeyPrefixCommentCallbackRef = useRef<(result: IRedisKeyPrefixCommentResult) => void>();
  const mappingCallbackRef = useRef<(result: IFieldMappingResult) => void>();
  const expressionCallbackRef = useRef<(result: IDataExpressionResult) => void>();
  const sqlFixCallbackRef = useRef<(sql: string) => void>();
  const extractedSqlRef = useRef<string | null>(null);
  const [tableSelectorOpen, setTableSelectorOpen] = useState(false);
  const [tableSelectorLoading, setTableSelectorLoading] = useState(false);
  const [relatedTablesLoading, setRelatedTablesLoading] = useState(false);
  const [databaseTables, setDatabaseTables] = useState<Array<{ name: string; comment?: string }>>([]);
  const [selectedTableDraft, setSelectedTableDraft] = useState<string[]>([]);
  const [mentionActive, setMentionActive] = useState(false);
  const [pendingImages, setPendingImages] = useState<IChatImageAttachment[]>([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
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
    renameSession,
    lastRequest,
  } = useAiChatStore();

  const currentSession = currentSessionId ? sessions[currentSessionId] : null;

  const addImageFiles = useCallback(async (files: File[]) => {
    const validFiles = files.filter((file) => {
      if (!SUPPORTED_IMAGE_TYPES.includes(file.type)) {
        message.warning(`${file.name} 不是支持的图片格式`);
        return false;
      }
      if (file.size > MAX_IMAGE_SIZE) {
        message.warning(`${file.name} 超过 5 MB`);
        return false;
      }
      return true;
    });

    let attachments: IChatImageAttachment[];
    try {
      attachments = await Promise.all(
        validFiles.map(
          (file) =>
            new Promise<IChatImageAttachment>((resolve, reject) => {
              const reader = new FileReader();
              reader.onload = () =>
                resolve({
                  id: uuidv4(),
                  name: file.name,
                  mimeType: file.type,
                  dataUrl: String(reader.result),
                  size: file.size,
                });
              reader.onerror = () => reject(reader.error);
              reader.readAsDataURL(file);
            }),
        ),
      );
    } catch (error) {
      console.error('[AiChat] Failed to read image:', error);
      message.error('读取图片失败');
      return;
    }

    setPendingImages((current) => {
      const remaining = MAX_IMAGE_COUNT - current.length;
      if (attachments.length > remaining) {
        message.warning(`单次最多上传 ${MAX_IMAGE_COUNT} 张图片`);
      }
      return [...current, ...attachments.slice(0, Math.max(remaining, 0))];
    });
  }, []);

  const { consoleList, workspaceTabList, activeConsoleId, currentConnectionDetails, pendingAiChat } =
    useWorkspaceStore((state) => ({
      consoleList: state.consoleList,
      workspaceTabList: state.workspaceTabList,
      activeConsoleId: state.activeConsoleId,
      currentConnectionDetails: state.currentConnectionDetails,
      pendingAiChat: state.pendingAiChat,
    }));

  const activeConsole =
    workspaceTabList?.find((tab) => String(tab.id) === String(activeConsoleId))?.uniqueData ||
    consoleList?.find((console) => String(console.id) === String(activeConsoleId));

  const [boundInfo, setBoundInfo] = useState(() => ({
    dataSourceId: currentSession?.dataSourceId ?? activeConsole?.dataSourceId ?? currentConnectionDetails?.id,
    databaseName: currentSession?.databaseName ?? activeConsole?.databaseName ?? '',
    schemaName: currentSession?.schemaName ?? activeConsole?.schemaName ?? '',
    tableNames: currentSession?.selectedTables || pendingAiChat?.tableNames || null,
  }));

  useEffect(() => {
    const activeConsoleInfo =
      workspaceTabList?.find((tab) => String(tab.id) === String(activeConsoleId))?.uniqueData ||
      consoleList?.find((console) => String(console.id) === String(activeConsoleId));
    if (activeConsoleInfo) {
      setBoundInfo((prev) => ({
        dataSourceId: activeConsoleInfo.dataSourceId ?? prev.dataSourceId,
        databaseName: activeConsoleInfo.databaseName ?? '',
        schemaName: activeConsoleInfo.schemaName ?? '',
        tableNames: prev.tableNames,
      }));
    }
  }, [activeConsoleId, consoleList, workspaceTabList]);

  useEffect(() => {
    // 待执行任务会提供明确的数据源上下文，不能被旧会话的上下文覆盖。
    if (!currentSession || pendingAiChat?.message) {
      return;
    }
    setBoundInfo({
      dataSourceId: currentSession.dataSourceId ?? undefined,
      databaseName: currentSession.databaseName ?? '',
      schemaName: currentSession.schemaName ?? '',
      tableNames: currentSession.selectedTables || null,
    });
  }, [
    currentSessionId,
    currentSession?.dataSourceId,
    currentSession?.databaseName,
    currentSession?.schemaName,
    pendingAiChat,
  ]);

  useEffect(() => {
    setDatabaseTables([]);
  }, [boundInfo.dataSourceId, boundInfo.databaseName, boundInfo.schemaName]);

  useEffect(() => {
    if (currentSession?.transient) {
      setSidebarCollapsed(true);
    }
  }, [currentSession?.transient]);

  const generateConversationTitle = useCallback(
    async (
      sessionId: string,
      titleSource: string,
      info: {
        dataSourceId?: number;
        databaseName?: string;
        schemaName?: string | null;
      },
    ) => {
      if (!titleSource?.trim()) {
        return;
      }

      closeTitleEventSource.current?.();
      const transientConversationId = uuidv4();
      let generatedTitle = '';
      const requestParams = {
        conversationId: transientConversationId,
        message: titleSource,
        promptType: 'TITLE_GENERATION',
        dataSourceId: info.dataSourceId,
        databaseName: info.databaseName,
        schemaName: info.schemaName,
      };

      let params = formatParams(requestParams);
      if (`/api/ai/chat?${params}`.length > MAX_DIRECT_SSE_URL_LENGTH) {
        try {
          const payloadId = await createChatPayload(requestParams);
          params = formatParams({
            payloadId,
            dataSourceId: info.dataSourceId,
            databaseName: info.databaseName,
            schemaName: info.schemaName,
          });
        } catch (error) {
          console.warn('[AiChat] Create title payload failed:', error);
          return;
        }
      }

      closeTitleEventSource.current = connectToEventSource({
        url: `/api/ai/chat?${params}`,
        uid: transientConversationId,
        onMessage: (content?: string) => {
          if (content) {
            generatedTitle += content;
          }
        },
        onDone: () => {
          closeTitleEventSource.current = undefined;
          const cleanTitle = generatedTitle
            .replace(/^["'`]+|["'`]+$/g, '')
            .replace(/\s+/g, ' ')
            .trim();
          if (cleanTitle) {
            renameSession(sessionId, cleanTitle).catch((error) => {
              console.warn('[AiChat] Rename generated title failed:', error);
            });
          }
        },
        onError: (error) => {
          closeTitleEventSource.current = undefined;
          console.warn('[AiChat] Generate title failed:', error);
        },
      });
    },
    [renameSession],
  );

  const sendAiChat = (
    messageText: string,
    promptType: IAiChatPromptType = 'NL_2_SQL',
    tableNames?: string[] | null,
    ext?: string,
    images: IChatImageAttachment[] = pendingImages,
  ) => {
    const infoWithTables = {
      ...boundInfo,
      tableNames: tableNames !== undefined ? tableNames : boundInfo.tableNames,
    };
    sendAiChatInternal(messageText, promptType, infoWithTables, ext, images);
  };

  const syncSelectedTables = useCallback(
    (tables: string[]) => {
      const nextTables = Array.from(new Set(tables.filter(Boolean)));
      setBoundInfo((prev) => ({
        ...prev,
        tableNames: nextTables.length ? nextTables : null,
      }));
      if (currentSessionId) {
        setSelectedTables(currentSessionId, nextTables);
      }
    },
    [currentSessionId, setSelectedTables],
  );

  const loadDatabaseTables = useCallback(async () => {
    if (!boundInfo.dataSourceId) {
      message.warning('请先选择数据库连接');
      return;
    }
    if (databaseTables.length > 0 || tableSelectorLoading) {
      return;
    }

    setTableSelectorLoading(true);
    try {
      const tables = await sqlService.getAllTableList({
        dataSourceId: boundInfo.dataSourceId,
        databaseName: boundInfo.databaseName,
        schemaName: boundInfo.schemaName,
      });
      setDatabaseTables(tables || []);
    } catch (error) {
      console.error('[AiChat] Failed to load tables:', error);
      message.error('加载表列表失败');
    } finally {
      setTableSelectorLoading(false);
    }
  }, [
    boundInfo.dataSourceId,
    boundInfo.databaseName,
    boundInfo.schemaName,
    databaseTables.length,
    tableSelectorLoading,
  ]);

  const openTableSelector = useCallback(() => {
    if (!boundInfo.dataSourceId) {
      message.warning('请先选择数据库连接');
      return;
    }

    const currentTables = currentSession?.selectedTables || boundInfo.tableNames || [];
    setSelectedTableDraft(currentTables);
    setTableSelectorOpen(true);
    loadDatabaseTables();
  }, [boundInfo.dataSourceId, boundInfo.tableNames, currentSession?.selectedTables, loadDatabaseTables]);

  const handleRemoveSelectedTable = useCallback(
    (tableName: string) => {
      const currentTables = currentSession?.selectedTables || boundInfo.tableNames || [];
      syncSelectedTables(currentTables.filter((item) => item !== tableName));
    },
    [boundInfo.tableNames, currentSession?.selectedTables, syncSelectedTables],
  );

  const handleConfirmTableSelector = useCallback(() => {
    syncSelectedTables(selectedTableDraft);
    setTableSelectorOpen(false);
    setInputValue((value) => (value.endsWith('@') ? value.slice(0, -1) : value));
  }, [selectedTableDraft, syncSelectedTables]);

  const handleAddRelatedTables = useCallback(async () => {
    const currentTables = currentSession?.selectedTables || boundInfo.tableNames || [];
    if (!currentTables.length) {
      message.warning('请先选择表');
      return;
    }
    if (!boundInfo.dataSourceId || !boundInfo.databaseName) {
      message.warning('请先选择数据库连接');
      return;
    }

    setRelatedTablesLoading(true);
    try {
      const foreignKeys = (
        await Promise.all(
          currentTables.map((tableName) =>
            sqlService.getForeignKeyList({
              dataSourceId: boundInfo.dataSourceId,
              databaseName: boundInfo.databaseName,
              schemaName: boundInfo.schemaName,
              tableName,
              relation: 'ANY',
            }),
          ),
        )
      ).flat();
      const selectedTableSet = new Set(currentTables);
      const nextTables = [...currentTables];

      (foreignKeys || []).forEach((foreignKey) => {
        let relatedTable: string | null = null;
        if (selectedTableSet.has(foreignKey.tableName)) {
          relatedTable = foreignKey.referencedTable;
        } else if (selectedTableSet.has(foreignKey.referencedTable)) {
          relatedTable = foreignKey.tableName;
        }
        if (relatedTable && !nextTables.includes(relatedTable)) {
          nextTables.push(relatedTable);
        }
      });

      if (nextTables.length === currentTables.length) {
        message.info('未找到新的关联表');
        return;
      }

      syncSelectedTables(nextTables);
      message.success(`已添加 ${nextTables.length - currentTables.length} 张关联表`);
    } catch (error) {
      console.error('[AiChat] Failed to load related tables:', error);
      message.error('加载关联表失败');
    } finally {
      setRelatedTablesLoading(false);
    }
  }, [boundInfo, currentSession?.selectedTables, syncSelectedTables]);

  const sendAiChatInternal = useCallback(
    async (
      messageText: string,
      requestedPromptType: IAiChatPromptType,
      info: typeof boundInfo,
      ext?: string,
      images: IChatImageAttachment[] = [],
    ) => {
      if (!messageText.trim() && images.length === 0) {
        message.warning('请输入问题');
        return;
      }

      const effectiveMessage = messageText.trim() || '请分析这些图片';

      if (!info.dataSourceId) {
        message.warning('请先选择数据库连接');
        return;
      }

      const storeState = useAiChatStore.getState();
      const existingSessionId = storeState.currentSessionId;
      const existingSession = existingSessionId ? storeState.sessions[existingSessionId] : null;
      const previousRequest = storeState.lastRequest;
      const promptType =
        requestedPromptType === 'NL_2_SQL' && existingSession?.promptType
          ? (existingSession.promptType as IAiChatPromptType)
          : requestedPromptType;
      console.log('[AiChat] sendAiChat called with:', { messageText: effectiveMessage, promptType, info });
      const isSimpleTask = isSimpleTaskPromptType(promptType);
      const isConversationalTask = isConversationalSqlPromptType(promptType);
      const isSameConnection =
        existingSession
          ? isSameDataSource(existingSession.dataSourceId, info.dataSourceId) &&
            normalizeNullableText(existingSession.databaseName) === normalizeNullableText(info.databaseName) &&
            normalizeNullableText(existingSession.schemaName) === normalizeNullableText(info.schemaName)
          : !previousRequest ||
            (isSameDataSource(previousRequest.dataSourceId, info.dataSourceId) &&
              normalizeNullableText(previousRequest.databaseName) === normalizeNullableText(info.databaseName) &&
              normalizeNullableText(previousRequest.schemaName) === normalizeNullableText(info.schemaName));
      const canReuseConversation =
        isConversationalTask &&
        isSameConnection &&
        !!existingSessionId &&
        !!existingSession &&
        existingSession.messages.length > 0 &&
        (!existingSession.promptType || existingSession.promptType === promptType) &&
        !isActiveState(existingSession.state);
      const canUseEmptyConversation =
        isConversationalTask &&
        promptType === 'NL_2_SQL' &&
        isSameConnection &&
        !!existingSessionId &&
        !!existingSession &&
        existingSession.messages.length === 0 &&
        !isActiveState(existingSession.state);
      const shouldReuseConversation = canReuseConversation || canUseEmptyConversation;
      const requestTableNames =
        shouldReuseConversation &&
        (!info.tableNames || info.tableNames.length === 0) &&
        existingSession?.selectedTables?.length
          ? existingSession.selectedTables
          : info.tableNames;
      const previousImages = shouldReuseConversation
        ? existingSession?.messages.flatMap((item) => item.images || []) || []
        : [];
      const currentImageIds = new Set(images.map((image) => image.id));
      const requestImages = [
        ...previousImages
          .filter((image) => !currentImageIds.has(image.id))
          .slice(-Math.max(MAX_IMAGE_COUNT - images.length, 0)),
        ...images,
      ];

      const sessionId = shouldReuseConversation ? existingSessionId! : uuidv4();
      console.log('[AiChat] Using sessionId:', sessionId, 'reuse:', shouldReuseConversation);
      sessionIdRef.current = sessionId;
      const defaultSessionTitle = getDefaultSessionTitle(promptType, effectiveMessage);
      const sessionTitle =
        shouldReuseConversation && existingSession?.title && existingSession.title !== '新对话'
          ? existingSession.title
          : defaultSessionTitle;
      if (!shouldReuseConversation) {
        createSession(sessionId, {
          dataSourceId: info.dataSourceId,
          databaseName: info.databaseName,
          schemaName: info.schemaName,
          selectedTables: requestTableNames || undefined,
          title: sessionTitle,
          promptType,
          transient: isSimpleTask,
        });
        if (!isSimpleTask) {
          try {
            await aiConversationService.createAiConversation({
              conversationId: sessionId,
              dataSourceId: info.dataSourceId,
              databaseName: info.databaseName,
              schemaName: info.schemaName,
              title: sessionTitle,
              promptType,
            });
          } catch (error) {
            console.warn('[AiChat] Create conversation before chat failed, backend will retry:', error);
          }
        }
      } else if (canUseEmptyConversation && existingSession?.title !== sessionTitle) {
        await renameSession(sessionId, sessionTitle);
      }

      const userMessageId = uuidv4();
      const assistantMessageId = uuidv4();
      const userMessage: IChatMessage = {
        id: userMessageId,
        role: 'user',
        content: effectiveMessage,
        images,
      };
      addMessage(sessionId, userMessage);

      setLastRequest({
        message: effectiveMessage,
        promptType,
        dataSourceId: info.dataSourceId,
        databaseName: info.databaseName,
        schemaName: info.schemaName,
        tableNames: requestTableNames,
        ext,
        images,
        conversationId: sessionId,
      });

      resetCurrentContent(sessionId);

      const requestParams = {
        message: effectiveMessage,
        promptType,
        dataSourceId: info.dataSourceId,
        databaseName: info.databaseName,
        schemaName: info.schemaName,
        tableNames: requestTableNames,
        ext,
        conversationId: sessionId,
        userMessageId,
        assistantMessageId,
        images: requestImages.map((image) => ({
          name: image.name,
          mimeType: image.mimeType,
          data: image.dataUrl.slice(image.dataUrl.indexOf(',') + 1),
        })),
      };

      setInputValue('');
      setPendingImages([]);

      const containsImages = requestImages.length > 0;
      let params = containsImages ? '' : formatParams(requestParams);
      if (containsImages || `/api/ai/chat?${params}`.length > MAX_DIRECT_SSE_URL_LENGTH) {
        try {
          const payloadId = await createChatPayload(requestParams);
          params = formatParams({
            payloadId,
            dataSourceId: info.dataSourceId,
            databaseName: info.databaseName,
            schemaName: info.schemaName,
          });
        } catch (error: any) {
          console.error('[AiChat] Failed to create chat payload:', error);
          setError(sessionId, error?.message || '创建 AI 请求失败');
          message.error('创建 AI 请求失败');
          return;
        }
      }

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
          console.log('[AiChat] Message received:', { content });
          appendContent(sessionId, content);
        },
        onTablesSelected: (tables) => {
          console.log('[AiChat] Tables selected:', tables);
          setSelectedTables(sessionId, tables);
          setBoundInfo((prev) => ({
            ...prev,
            tableNames: tables.length ? tables : null,
          }));
        },
        onSchemaFetched: (ddl) => {
          console.log('[AiChat] Schema fetched, ddl length:', ddl?.length);
          setSchemaInfo(sessionId, ddl);
        },
        onExplain: (data) => {
          console.log('[AiChat] Explain result:', data);
          useAiChatStore.getState().setExplainResult(sessionId, data);
        },
        onDone: () => {
          console.log('[AiChat] onDone callback, sessionId:', sessionId);
          updateState(sessionId, 'COMPLETED');
          const currentSessions = useAiChatStore.getState().sessions;
          console.log('[AiChat] sessions in onDone:', currentSessions);
          const session = currentSessions[sessionId];
          console.log('[AiChat] session in onDone:', session);
          if (session?.currentContent) {
            const shouldGenerateTitle =
              promptType === 'NL_2_SQL' &&
              session.messages.filter((item) => item.role === 'user').length === 1 &&
              (!session.title || session.title === '新对话' || session.title === 'NL_2_SQL');
            addMessage(sessionId, {
              id: assistantMessageId,
              role: 'assistant',
              content: session.currentContent,
            });
            if (shouldGenerateTitle) {
              const firstUserMessage = session.messages.find((item) => item.role === 'user')?.content;
              void generateConversationTitle(sessionId, firstUserMessage || effectiveMessage, info);
            }

            if (promptType === 'NL_2_COMMENT' && commentCallbackRef.current) {
              try {
                const jsonContent = extractJsonFromContent(session.currentContent);
                if (jsonContent) {
                  console.log('[AiChat] Parsed comment result:', jsonContent);
                  commentCallbackRef.current(jsonContent);
                  message.success('AI 注释已生成，请查看并确认');
                }
              } catch (e) {
                console.error('[AiChat] Failed to parse comment JSON:', e);
                message.warning('无法解析 AI 生成的注释，请手动查看');
              }
              commentCallbackRef.current = undefined;
            }

            if (promptType === 'NL_2_COMMENT_BATCH' && batchCommentCallbackRef.current) {
              try {
                const jsonContent = extractBatchJsonFromContent(session.currentContent);
                if (jsonContent) {
                  console.log('[AiChat] Parsed batch comment result:', jsonContent);
                  batchCommentCallbackRef.current(jsonContent);
                  message.success('AI 批量注释已生成');
                }
              } catch (e) {
                console.error('[AiChat] Failed to parse batch comment JSON:', e);
                message.warning('无法解析 AI 生成的批量注释，请手动查看');
              }
              batchCommentCallbackRef.current = undefined;
            }

            if (promptType === 'REDIS_KEYS_COMMENT' && redisKeyPrefixCommentCallbackRef.current) {
              try {
                const jsonContent = extractRedisKeyPrefixCommentFromContent(session.currentContent);
                if (jsonContent) {
                  console.log('[AiChat] Parsed Redis key prefix comment result:', jsonContent);
                  redisKeyPrefixCommentCallbackRef.current(jsonContent);
                  message.success('AI Redis key 注释已生成');
                }
              } catch (e) {
                console.error('[AiChat] Failed to parse Redis key prefix comment JSON:', e);
                message.warning('无法解析 AI 生成的 Redis key 注释，请手动查看');
              }
              redisKeyPrefixCommentCallbackRef.current = undefined;
            }

            if (promptType === 'NL_2_FIELD_MAPPING' && mappingCallbackRef.current) {
              try {
                const jsonContent = extractFieldMappingFromContent(session.currentContent);
                if (jsonContent) {
                  console.log('[AiChat] Parsed field mapping result:', jsonContent);
                  mappingCallbackRef.current(jsonContent);
                  message.success('AI 字段映射推荐已生成，请查看并确认');
                }
              } catch (e) {
                console.error('[AiChat] Failed to parse field mapping JSON:', e);
                message.warning('无法解析 AI 生成的映射推荐，请手动查看');
              }
              mappingCallbackRef.current = undefined;
            }

            if (promptType === 'NL_2_DATA_EXPRESSION' && expressionCallbackRef.current) {
              try {
                const jsonContent = extractDataExpressionFromContent(session.currentContent);
                if (jsonContent) {
                  console.log('[AiChat] Parsed data expression result:', jsonContent);
                  expressionCallbackRef.current(jsonContent);
                  message.success('AI 表达式推荐已生成，请查看并确认');
                }
              } catch (e) {
                console.error('[AiChat] Failed to parse data expression JSON:', e);
                message.warning('无法解析 AI 生成的表达式，请手动查看');
              }
              expressionCallbackRef.current = undefined;
            }

            if (promptType === 'SQL_FIX' && sqlFixCallbackRef.current) {
              try {
                const sql = extractDatabaseStatement(session.currentContent)?.statement;
                if (sql) {
                  console.log('[AiChat] Extracted SQL:', sql);
                  extractedSqlRef.current = sql;
                  sqlFixCallbackRef.current(sql);
                  message.success('AI 已修复SQL，请查看并确认');
                } else {
                  message.warning('AI 未能生成修复后的SQL');
                }
              } catch (e) {
                console.error('[AiChat] Failed to extract SQL:', e);
                message.warning('无法提取 AI 生成的SQL');
              }
              sqlFixCallbackRef.current = undefined;
            }
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
      createSession,
      addMessage,
      setLastRequest,
      resetCurrentContent,
      updateState,
      appendContent,
      setSelectedTables,
      setSchemaInfo,
      setError,
      renameSession,
      generateConversationTitle,
    ],
  );

  useEffect(() => {
    if (pendingAiChat && pendingAiChat.message) {
      const overrideBoundInfo = {
        dataSourceId: pendingAiChat.dataSourceId ?? boundInfo.dataSourceId,
        databaseName: pendingAiChat.databaseName ?? boundInfo.databaseName ?? '',
        schemaName: pendingAiChat.schemaName ?? boundInfo.schemaName ?? '',
        tableNames: pendingAiChat.tableNames ?? boundInfo.tableNames ?? null,
      };
      // 入口页面传入的上下文优先，四个字段必须一起切换，避免出现“数据源已变但数据库仍是旧值”。
      setBoundInfo(overrideBoundInfo);
      if (pendingAiChat.onCommentGenerated) {
        commentCallbackRef.current = pendingAiChat.onCommentGenerated;
      }
      if (pendingAiChat.onBatchCommentGenerated) {
        batchCommentCallbackRef.current = pendingAiChat.onBatchCommentGenerated;
      }
      if (pendingAiChat.onRedisKeyPrefixCommentGenerated) {
        redisKeyPrefixCommentCallbackRef.current = pendingAiChat.onRedisKeyPrefixCommentGenerated;
      }
      if (pendingAiChat.onMappingGenerated) {
        mappingCallbackRef.current = pendingAiChat.onMappingGenerated;
      }
      if (pendingAiChat.onExpressionGenerated) {
        expressionCallbackRef.current = pendingAiChat.onExpressionGenerated;
      }
      if (pendingAiChat.onSqlFixed) {
        sqlFixCallbackRef.current = pendingAiChat.onSqlFixed;
      }
      sendAiChatInternal(pendingAiChat.message, pendingAiChat.promptType, overrideBoundInfo, pendingAiChat.ext);
      useWorkspaceStore.setState({ pendingAiChat: null });
    }
  }, [pendingAiChat, boundInfo, sendAiChatInternal]);

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

  useEffect(() => {
    return () => {
      closeTitleEventSource.current?.();
    };
  }, []);

  const handleExecuteStatement = useCallback((statement: string, type: DatabaseStatementType) => {
    const activeConsoleInfo = consoleList?.find((c) => c.id === activeConsoleId);
    if (activeConsoleInfo) {
      const searchResultRef = getSearchResult(activeConsoleInfo.id);
      if (searchResultRef?.current) {
        searchResultRef.current.handleExecuteSQL(statement);
        message.success(`${type}已执行`);
      } else {
        message.warning('未找到执行结果组件');
      }
    }
  }, [consoleList, activeConsoleId]);

  const handleSendToEditor = useCallback((statement: string, type: DatabaseStatementType) => {
    const activeConsoleInfo = consoleList?.find((c) => c.id === activeConsoleId);
    if (activeConsoleInfo) {
      const consoleRef = getConsoleEditor(activeConsoleInfo.id);
      if (consoleRef?.current?.editorRef) {
        consoleRef.current.editorRef.setValue(statement, 'cover');
        message.success(`${type}已发送到编辑器`);
      } else {
        message.warning('未找到编辑器组件');
      }
    }
  }, [consoleList, activeConsoleId]);

  const handleRetry = () => {
    if (lastRequest) {
      sendAiChat(
        lastRequest.message,
        lastRequest.promptType as IAiChatPromptType,
        lastRequest.tableNames,
        lastRequest.ext,
        lastRequest.images,
      );
    }
  };

  const handleInputKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (mentionActive) {
      return;
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendAiChat(inputValue);
    }
  };

  const handleMentionKeyUp = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const textBeforeCursor = e.currentTarget.value.slice(0, e.currentTarget.selectionStart || 0);
    setMentionActive(/@[^\s@]*$/.test(textBeforeCursor));
  };

  const handleMentionSearch = () => {
    setMentionActive(true);
    loadDatabaseTables();
  };

  const handleMentionSelect = (option: { value: string }) => {
    const currentTables = currentSession?.selectedTables || boundInfo.tableNames || [];
    syncSelectedTables([...currentTables, option.value]);
    setMentionActive(false);
  };

  const handleImagePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const files = Array.from(e.clipboardData.items)
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .filter((file): file is File => file !== null);
    if (files.length > 0) {
      e.preventDefault();
      void addImageFiles(files);
    }
  };

  const handleRemovePendingImage = (imageId: string) => {
    setPendingImages((images) => images.filter((image) => image.id !== imageId));
  };

  const handleBoundInfoChange = (value: { dataSourceId: number; databaseName: string; schemaName: string }) => {
    setBoundInfo({
      dataSourceId: value.dataSourceId,
      databaseName: value.databaseName,
      schemaName: value.schemaName,
      tableNames: null,
    });
  };

  const handleNewConversation = useCallback(
    (newConversationBoundInfo?: {
      dataSourceId?: number | null;
      databaseName?: string | null;
      schemaName?: string | null;
    }) => {
      setBoundInfo((prev) => ({
        dataSourceId: newConversationBoundInfo?.dataSourceId ?? prev.dataSourceId,
        databaseName: newConversationBoundInfo?.databaseName ?? '',
        schemaName: newConversationBoundInfo?.schemaName ?? '',
        tableNames: null,
      }));
      setSelectedTableDraft([]);
      setInputValue('');
      setPendingImages([]);
    },
    [],
  );

  const newConversationBoundInfo = activeConsole
    ? {
        dataSourceId: activeConsole.dataSourceId,
        databaseName: activeConsole.databaseName ?? '',
        schemaName: activeConsole.schemaName ?? '',
      }
    : {
        dataSourceId: boundInfo.dataSourceId ?? currentConnectionDetails?.id,
        databaseName: boundInfo.databaseName,
        schemaName: boundInfo.schemaName,
      };

  const stateInfo = currentSession ? STATE_LABELS[currentSession.state] : null;
  const isProcessing = isActiveState(currentSession?.state);
  const isInputDisabled = isProcessing || !boundInfo.dataSourceId;
  const selectedTablesForDisplay = currentSession ? currentSession.selectedTables || [] : boundInfo.tableNames || [];
  const tableOptions = databaseTables.map((table) => ({
    label: table.comment ? `${table.name} - ${table.comment}` : table.name,
    value: table.name,
  }));

  return (
    <div className={styles.aiChatContainer}>
      <div className={`${styles.sidebarLayout} ${sidebarCollapsed ? styles.sidebarLayoutCollapsed : ''}`}>
        <ConversationSidebar
          newConversationBoundInfo={newConversationBoundInfo}
          collapsed={sidebarCollapsed}
          onCollapsedChange={setSidebarCollapsed}
          onNewConversation={handleNewConversation}
        />
      </div>
      <div className={styles.mainPanel}>
      {currentSession && (
        <div className={styles.chatHeader}>
          <span className={styles.chatHeaderTitle}>
            {currentSession.title || (currentSession.messages[0]?.content || '新对话').slice(0, 30)}
          </span>
        </div>
      )}
      {stateInfo && (
        <div className={styles.statusBar}>
          <Tag color={stateInfo.color}>{stateInfo.text}</Tag>
          <div className={styles.selectedTables}>
            <span>已选择表：</span>
            {selectedTablesForDisplay.map((tableName) => (
              <Tag
                key={tableName}
                color="blue"
                closable={!isProcessing}
                onClose={(event) => {
                  event.preventDefault();
                  handleRemoveSelectedTable(tableName);
                }}
              >
                {tableName}
              </Tag>
            ))}
            <Button
              size="small"
              icon={<PlusOutlined />}
              onClick={openTableSelector}
              disabled={isProcessing || !boundInfo.dataSourceId}
            >
              @ 表
            </Button>
            <Button
              size="small"
              icon={<LinkOutlined />}
              onClick={handleAddRelatedTables}
              loading={relatedTablesLoading}
              disabled={isProcessing || !boundInfo.dataSourceId || selectedTablesForDisplay.length === 0}
            >
              关联表
            </Button>
            {selectedTablesForDisplay.length > 0 && (
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => syncSelectedTables([])}
                disabled={isProcessing}
              >
                清空
              </Button>
            )}
          </div>
          {isProcessing && (
            <Button size="small" danger onClick={handleCancel}>
              停止
            </Button>
          )}
        </div>
      )}

      <div className={styles.contentArea}>
        {currentSession?.messages.map((msg) => {
          const databaseStatement = msg.role === 'assistant' ? extractDatabaseStatement(msg.content) : null;
          const markdownContent = msg.role === 'assistant' ? normalizeDatabaseMarkdown(msg.content) : msg.content;
          return (
            <div key={msg.id} className={msg.role === 'user' ? styles.userBlock : styles.aiBlock}>
              {!!msg.images?.length && (
                <div className={styles.messageImages}>
                  {msg.images.map((image) => (
                    <img key={image.id} src={image.dataUrl} alt={image.name} title={image.name} />
                  ))}
                </div>
              )}
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
                {markdownContent}
              </ReactMarkdown>
              {databaseStatement && msg.role === 'assistant' && (
                <DatabaseStatementActionButtons
                  statement={databaseStatement.statement}
                  type={databaseStatement.type}
                  onExecute={handleExecuteStatement}
                  onSendToEditor={handleSendToEditor}
                />
              )}
            </div>
          );
        })}
        {currentSession?.explainResult && currentSession.explainResult.success && (
          <ExplainPanel explainResult={currentSession.explainResult} />
        )}
        {currentSession?.state === 'STREAMING' && currentSession.currentContent && (
          <div className={styles.aiBlock}>
            <Spin size="small">
              {currentSession.currentContent && (
                <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
                  {normalizeDatabaseMarkdown(currentSession.currentContent)}
                </ReactMarkdown>
              )}
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
        {pendingImages.length > 0 && (
          <div className={styles.pendingImages}>
            {pendingImages.map((image) => (
              <div className={styles.pendingImage} key={image.id}>
                <img src={image.dataUrl} alt={image.name} />
                <Button
                  className={styles.removeImageButton}
                  type="text"
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => handleRemovePendingImage(image.id)}
                />
              </div>
            ))}
          </div>
        )}
        <Mentions
          className={styles.chatInput}
          value={inputValue}
          onChange={setInputValue}
          onKeyDown={handleInputKeyDown}
          onKeyUp={handleMentionKeyUp}
          onSearch={handleMentionSearch}
          onSelect={handleMentionSelect}
          onPaste={handleImagePaste}
          options={tableOptions}
          loading={tableSelectorLoading}
          placement="top"
          placeholder="请输入您的问题..."
          autoSize={{ minRows: 3, maxRows: 6 }}
          disabled={isProcessing}
        />
        <div className={styles.buttonGroup}>
          <Upload
            accept={SUPPORTED_IMAGE_TYPES.join(',')}
            multiple
            showUploadList={false}
            beforeUpload={(file) => {
              void addImageFiles([file]);
              return Upload.LIST_IGNORE;
            }}
          >
            <Button
              type="text"
              icon={<PaperClipOutlined />}
              disabled={isProcessing || pendingImages.length >= MAX_IMAGE_COUNT}
            >
              图片
            </Button>
          </Upload>
          <Button
            type="primary"
            onClick={() => sendAiChat(inputValue)}
            disabled={(!inputValue.trim() && pendingImages.length === 0) || isInputDisabled}
          >
            发送
          </Button>
        </div>
      </div>
      <Modal
        title="@ 当前库的表"
        open={tableSelectorOpen}
        onOk={handleConfirmTableSelector}
        onCancel={() => setTableSelectorOpen(false)}
        okText="添加引用"
        cancelText="取消"
      >
        <Select
          mode="multiple"
          showSearch
          allowClear
          loading={tableSelectorLoading}
          value={selectedTableDraft}
          options={tableOptions}
          placeholder="搜索并选择表"
          optionFilterProp="label"
          onChange={setSelectedTableDraft}
          style={{ width: '100%' }}
        />
      </Modal>
      </div>
    </div>
  );
});

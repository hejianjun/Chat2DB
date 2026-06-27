import React, { memo, useEffect, useState, useRef } from 'react';
import { Button, Input, Modal, Dropdown, message, Spin, type MenuProps } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  MoreOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import i18n from '@/i18n';
import { useAiChatStore, AiChatSession } from '@/pages/main/workspace/store/aiChatStore';
import connectToEventSource, { createChatPayload } from '@/utils/eventSource';
import { formatParams } from '@/utils/url';
import { v4 as uuidv4 } from 'uuid';
import styles from './index.less';

const MAX_DIRECT_SSE_URL_LENGTH = 1800;

interface IConversationItemProps {
  session: AiChatSession;
  active: boolean;
  onClick: () => void;
  onDelete: () => void;
  onRename: (title: string) => void;
  onGenerateTitle: () => void;
  generatingTitle: boolean;
}

const ConversationItem = memo<IConversationItemProps>(({
  session,
  active,
  onClick,
  onDelete,
  onRename,
  onGenerateTitle,
  generatingTitle,
}) => {
  const [editing, setEditing] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [draft, setDraft] = useState(session.title || '');
  const inputRef = useRef<any>(null);

  useEffect(() => {
    if (editing) {
      inputRef.current?.focus();
      inputRef.current?.select?.();
    }
  }, [editing]);

  const handleSubmit = () => {
    const next = draft.trim();
    if (next && next !== session.title) {
      onRename(next);
    }
    setEditing(false);
  };

  const displayTitle = session.title || (session.messages[0]?.content || i18n('chat.header.titleFallback')).slice(0, 20);
  const menuItems: MenuProps['items'] = [
    {
      key: 'generateTitle',
      label: generatingTitle ? i18n('common.text.generatingTitle') : i18n('common.text.generateTitle'),
      icon: <RobotOutlined />,
      disabled: generatingTitle,
      onClick: ({ domEvent }) => {
        domEvent.stopPropagation();
        onGenerateTitle();
      },
    },
    {
      key: 'rename',
      label: i18n('chat.sidebar.rename'),
      icon: <EditOutlined />,
      onClick: ({ domEvent }) => {
        domEvent.stopPropagation();
        setEditing(true);
      },
    },
    {
      key: 'delete',
      label: i18n('chat.sidebar.delete'),
      icon: <DeleteOutlined />,
      danger: true,
      onClick: ({ domEvent }) => {
        domEvent.stopPropagation();
        onDelete();
      },
    },
  ];

  return (
    <Dropdown
      menu={{ items: menuItems }}
      trigger={['contextMenu']}
      open={menuOpen}
      onOpenChange={setMenuOpen}
      placement="bottomRight"
    >
      <div
        className={`${styles.conversationItem} ${active ? styles.conversationItemActive : ''}`}
        onClick={editing ? undefined : onClick}
      >
        <MessageOutlined className={styles.conversationItemIcon} />
        {editing ? (
          <Input
            ref={inputRef}
            size="small"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onPressEnter={handleSubmit}
            onBlur={handleSubmit}
            onClick={(e) => e.stopPropagation()}
            className={styles.conversationItemInput}
          />
        ) : (
          <span className={styles.conversationItemTitle} title={displayTitle}>
            {displayTitle}
          </span>
        )}
        <Dropdown menu={{ items: menuItems }} trigger={['click']} placement="bottomRight">
          <MoreOutlined className={styles.conversationItemMore} onClick={(e) => e.stopPropagation()} />
        </Dropdown>
      </div>
    </Dropdown>
  );
});
ConversationItem.displayName = 'ConversationItem';

export default memo((props: {
  boundInfo?: { dataSourceId?: number | null; databaseName?: string | null; schemaName?: string | null };
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
  onNewConversation?: () => void;
}) => {
  const {
    conversationList,
    conversationListLoading,
    conversationListHasMore,
    sessions,
    currentSessionId,
    startNewConversation,
    switchToSession,
    deleteSession,
    renameSession,
    loadConversationList,
  } = useAiChatStore();
  const [generatingTitleId, setGeneratingTitleId] = useState<string | null>(null);
  const closeEventSourceRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    loadConversationList(true);
  }, [loadConversationList]);

  const handleNew = () => {
    startNewConversation(props.boundInfo);
    props.onNewConversation?.();
  };

  const handleDelete = (conversationId: string) => {
    Modal.confirm({
      title: i18n('chat.sidebar.delete.confirm'),
      okText: i18n('chat.sidebar.delete'),
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => deleteSession(conversationId).catch(() => message.error('删除失败')),
    });
  };

  const handleRename = (conversationId: string, title: string) => {
    renameSession(conversationId, title).catch(() => message.error('重命名失败'));
  };

  const handleGenerateTitle = async (session: AiChatSession) => {
    if (generatingTitleId) {
      return;
    }

    const messageContent = session.messages.find((item) => item.role === 'user')?.content
      || session.messages[0]?.content
      || session.title;
    if (!messageContent?.trim()) {
      message.warning(i18n('workspace.tips.noSqlContent'));
      return;
    }

    setGeneratingTitleId(session.sessionId);
    let generatedTitle = '';
    const transientConversationId = uuidv4();
    const requestParams = {
      conversationId: transientConversationId,
      message: messageContent,
      promptType: 'TITLE_GENERATION',
      dataSourceId: session.dataSourceId,
      databaseName: session.databaseName,
      schemaName: session.schemaName,
    };

    let params = formatParams(requestParams);
    if (`/api/ai/chat?${params}`.length > MAX_DIRECT_SSE_URL_LENGTH) {
      try {
        const payloadId = await createChatPayload(requestParams);
        params = formatParams({
          payloadId,
          dataSourceId: session.dataSourceId,
          databaseName: session.databaseName,
          schemaName: session.schemaName,
        });
      } catch (error) {
        console.error('[ConversationSidebar] Failed to create title payload:', error);
        setGeneratingTitleId(null);
        message.error(i18n('workspace.tips.generateTitleFailed'));
        return;
      }
    }

    closeEventSourceRef.current = connectToEventSource({
      url: `/api/ai/chat?${params}`,
      uid: transientConversationId,
      onMessage: (content?: string) => {
        if (content) {
          generatedTitle += content;
        }
      },
      onDone: () => {
        setGeneratingTitleId(null);
        const cleanTitle = generatedTitle.replace(/^["'`]+|["'`]+$/g, '').trim();
        if (cleanTitle) {
          renameSession(session.sessionId, cleanTitle).then(() => message.success(i18n('common.tips.updateSuccess')));
        } else {
          message.warning(i18n('workspace.tips.generateTitleFailed'));
        }
      },
      onError: () => {
        setGeneratingTitleId(null);
        message.error(i18n('workspace.tips.generateTitleFailed'));
      },
    });
  };

  useEffect(() => {
    return () => {
      closeEventSourceRef.current?.();
    };
  }, []);

  if (props.collapsed) {
    return (
      <div className={`${styles.conversationSidebar} ${styles.conversationSidebarCollapsed}`}>
        <Button
          type="text"
          size="small"
          icon={<MenuUnfoldOutlined />}
          onClick={() => props.onCollapsedChange?.(false)}
          title={i18n('chat.sidebar.expand')}
        />
        <Button
          type="text"
          size="small"
          icon={<PlusOutlined />}
          onClick={handleNew}
          title={i18n('chat.sidebar.new')}
        />
      </div>
    );
  }

  return (
    <div className={styles.conversationSidebar}>
      <div className={styles.sidebarHeader}>
        <span className={styles.sidebarTitle}>{i18n('chat.sidebar.title')}</span>
        <div className={styles.sidebarHeaderActions}>
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            onClick={handleNew}
            title={i18n('chat.sidebar.new')}
          />
          <Button
            type="text"
            size="small"
            icon={<MenuFoldOutlined />}
            onClick={() => props.onCollapsedChange?.(true)}
            title={i18n('chat.sidebar.collapse')}
          />
        </div>
      </div>
      <div className={styles.sidebarList}>
        {conversationList.length === 0 && !conversationListLoading ? (
          <div className={styles.sidebarEmpty}>{i18n('chat.sidebar.empty')}</div>
        ) : (
          conversationList.map((item) => {
            const localSession = sessions[item.conversationId];
            const sessionForRender: AiChatSession =
              localSession ||
              ({
                sessionId: item.conversationId,
                state: 'COMPLETED',
                messages: item.lastMessagePreview
                  ? [{ id: 'preview', role: 'assistant' as const, content: item.lastMessagePreview }]
                  : [],
                currentContent: '',
                title: item.title,
                dataSourceId: item.dataSourceId,
                databaseName: item.databaseName,
                schemaName: item.schemaName,
                createdAt: 0,
                updatedAt: 0,
              } as AiChatSession);
            return (
              <ConversationItem
                key={item.conversationId}
                session={sessionForRender}
                active={currentSessionId === item.conversationId}
                onClick={() => switchToSession(item.conversationId)}
                onDelete={() => handleDelete(item.conversationId)}
                onRename={(title) => handleRename(item.conversationId, title)}
                onGenerateTitle={() => handleGenerateTitle(sessionForRender)}
                generatingTitle={generatingTitleId === item.conversationId}
              />
            );
          })
        )}
        {conversationListLoading && (
          <div className={styles.sidebarLoading}>
            <Spin size="small" />
          </div>
        )}
        {conversationListHasMore && !conversationListLoading && (
          <div
            className={styles.sidebarLoadMore}
            onClick={() => loadConversationList(false)}
          >
            加载更多
          </div>
        )}
      </div>
    </div>
  );
});

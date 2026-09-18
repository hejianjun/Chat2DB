import React, { useEffect, useMemo, useState } from 'react';
import i18n from '@/i18n';
import configServer from '@/service/config';
import mcpServer, { IMcpClientConnection, IMcpStatus } from '@/service/mcp';
import { Button, Input, Modal, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import classnames from 'classnames';
import styles from '../index.less';

const MCP_ENDPOINT = '/api/mcp';
const MCP_SERVER_CONFIG_CODE = 'mcp.server.config';

type McpAuthType = 'none' | 'bearer';

interface IMcpSqlConfig {
  allowWrite?: boolean;
  allowDdl?: boolean;
}

interface IMcpServerConfig {
  authType?: McpAuthType;
  token?: string;
  approvalClientKeywords?: string[];
  sql?: IMcpSqlConfig;
}

const normalizeApprovalClientKeywords = (keywords?: string[] | null): string[] => {
  const seen = new Set<string>();
  // 未配置时默认使用 codex，显式空数组表示所有客户端都请求 elicitation 确认。
  return (keywords ?? ['codex'])
    .map((keyword) => keyword.trim())
    .filter((keyword) => {
      const normalized = keyword.toLowerCase();
      if (!normalized || seen.has(normalized)) {
        return false;
      }
      seen.add(normalized);
      return true;
    });
};

export default function McpSetting() {
  const [storedConfig, setStoredConfig] = useState<IMcpServerConfig>({});
  const [mcpAuthType, setMcpAuthType] = useState<McpAuthType>('none');
  const [mcpToken, setMcpToken] = useState('');
  const [allowWrite, setAllowWrite] = useState(false);
  const [allowDdl, setAllowDdl] = useState(false);
  const [approvalClientKeywords, setApprovalClientKeywords] = useState<string[]>(normalizeApprovalClientKeywords);
  const [mcpStatus, setMcpStatus] = useState<IMcpStatus>();
  const [statusLoading, setStatusLoading] = useState(false);
  const [clientModalOpen, setClientModalOpen] = useState(false);
  const mcpAddress = useMemo(() => {
    return window._BaseURL ? `${window._BaseURL.replace(/\/+$/, '')}${MCP_ENDPOINT}` : '';
  }, []);

  useEffect(() => {
    configServer.getSystemConfig({ code: MCP_SERVER_CONFIG_CODE }).then((res) => {
      if (!res?.content) {
        return;
      }
      try {
        const config = JSON.parse(res.content) as IMcpServerConfig;
        // Remove the retired flag when an older saved configuration is applied again.
        delete (config.sql as (IMcpSqlConfig & { requireConfirmParam?: boolean }) | undefined)?.requireConfirmParam;
        setStoredConfig(config);
        setMcpAuthType(config.authType || 'none');
        setMcpToken(config.token || '');
        setAllowWrite(Boolean(config.sql?.allowWrite));
        setAllowDdl(Boolean(config.sql?.allowDdl));
        setApprovalClientKeywords(normalizeApprovalClientKeywords(config.approvalClientKeywords));
      } catch {
        setStoredConfig({});
      }
    });
  }, []);

  async function refreshMcpStatus() {
    setStatusLoading(true);
    try {
      setMcpStatus(await mcpServer.getStatus());
    } catch {
      setMcpStatus(undefined);
    } finally {
      setStatusLoading(false);
    }
  }

  useEffect(() => {
    refreshMcpStatus();
    const timer = window.setInterval(refreshMcpStatus, 5000);
    return () => window.clearInterval(timer);
  }, []);

  async function saveMcpConfig() {
    await configServer.setSystemConfig({
      code: MCP_SERVER_CONFIG_CODE,
      content: JSON.stringify({
        ...storedConfig,
        authType: mcpAuthType,
        token: mcpAuthType === 'bearer' ? mcpToken.trim() : '',
        approvalClientKeywords: normalizeApprovalClientKeywords(approvalClientKeywords),
        sql: {
          ...storedConfig.sql,
          allowWrite,
          allowDdl,
        },
      }),
    });
    message.success(i18n('setting.message.mcpConfigSaved'));
  }

  return (
    <>
      <div className={styles.title}>{i18n('setting.label.mcpAddress')}</div>
      <div className={classnames(styles.content, styles.chatGPTKey)}>
        <Input value={mcpAddress} readOnly />
      </div>
      <div className={styles.title}>{i18n('setting.label.mcpConnectionStatus')}</div>
      <div className={styles.content}>
        <Space>
          {mcpStatus?.connected ? (
            <Button type="link" size="small" onClick={() => setClientModalOpen(true)}>
              <Tag color="success">
                {i18n('setting.text.mcpConnected')} ({mcpStatus.connectionCount})
              </Tag>
            </Button>
          ) : (
            <Tag>{i18n('setting.text.mcpDisconnected')}</Tag>
          )}
          <Button size="small" loading={statusLoading} onClick={refreshMcpStatus}>
            {i18n('setting.button.refresh')}
          </Button>
        </Space>
      </div>
      <div className={styles.title}>{i18n('setting.label.mcpAuthType')}</div>
      <div className={classnames(styles.content, styles.chatGPTKey)}>
        <Select
          value={mcpAuthType}
          options={[
            { label: i18n('setting.option.mcpAuthNone'), value: 'none' },
            { label: i18n('setting.option.mcpAuthBearer'), value: 'bearer' },
          ]}
          onChange={setMcpAuthType}
        />
      </div>
      {mcpAuthType === 'bearer' && (
        <>
          <div className={styles.title}>{i18n('setting.label.mcpToken')}</div>
          <div className={classnames(styles.content, styles.chatGPTKey)}>
            <Input.Password value={mcpToken} onChange={(e) => setMcpToken(e.target.value)} />
          </div>
        </>
      )}
      <div className={styles.title}>{i18n('setting.label.mcpAllowWrite')}</div>
      <div className={classnames(styles.content, styles.chatGPTKey)}>
        <Switch checked={allowWrite} onChange={setAllowWrite} />
      </div>
      <div className={styles.title}>{i18n('setting.label.mcpAllowDdl')}</div>
      <div className={classnames(styles.content, styles.chatGPTKey)}>
        <Switch checked={allowDdl} onChange={setAllowDdl} />
      </div>
      <div className={styles.title}>{i18n('setting.label.mcpApprovalClientKeywords')}</div>
      <div className={styles.content}>
        <Select
          mode="tags"
          allowClear
          style={{ width: '100%' }}
          maxTagCount="responsive"
          maxTagTextLength={24}
          value={approvalClientKeywords}
          tokenSeparators={[',', '，']}
          placeholder={i18n('setting.placeholder.mcpApprovalClientKeywords')}
          aria-label={i18n('setting.label.mcpApprovalClientKeywords')}
          onChange={(keywords) => setApprovalClientKeywords(normalizeApprovalClientKeywords(keywords))}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
          {i18n('setting.text.mcpApprovalClientKeywords')}
        </Typography.Paragraph>
      </div>
      <div className={styles.bottomButton}>
        <Button type="primary" onClick={saveMcpConfig}>
          {i18n('setting.button.apply')}
        </Button>
      </div>
      <Modal
        title={i18n('setting.title.mcpClients')}
        open={clientModalOpen}
        footer={null}
        width={760}
        onCancel={() => setClientModalOpen(false)}
      >
        <Table<IMcpClientConnection>
          rowKey={(record) =>
            `${record.clientInfo?.name || ''}-${record.clientInfo?.version || ''}-${record.connectedAt}`
          }
          dataSource={mcpStatus?.clients || []}
          pagination={false}
          size="small"
          columns={[
            {
              title: i18n('setting.label.mcpClientName'),
              dataIndex: ['clientInfo', 'name'],
              render: (value) => value || '-',
            },
            {
              title: i18n('setting.label.mcpClientVersion'),
              dataIndex: ['clientInfo', 'version'],
              render: (value) => value || '-',
            },
            {
              title: i18n('setting.label.mcpProtocolVersion'),
              dataIndex: ['clientInfo', 'protocolVersion'],
              render: (value) => value || '-',
            },
            {
              title: i18n('setting.label.mcpConnectedAt'),
              dataIndex: 'connectedAt',
              render: (value) => new Date(value).toLocaleString(),
            },
            {
              title: i18n('setting.label.mcpLastActiveAt'),
              dataIndex: 'lastActiveAt',
              render: (value) => new Date(value).toLocaleString(),
            },
          ]}
        />
      </Modal>
    </>
  );
}

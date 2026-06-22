import React, { useEffect, useMemo, useState } from 'react';
import i18n from '@/i18n';
import configServer from '@/service/config';
import { Button, Input, Select, Switch, message } from 'antd';
import classnames from 'classnames';
import styles from '../index.less';

const MCP_ENDPOINT = '/api/mcp';
const MCP_SERVER_CONFIG_CODE = 'mcp.server.config';

type McpAuthType = 'none' | 'bearer';

interface IMcpSqlConfig {
  allowWrite?: boolean;
  allowDdl?: boolean;
  requireConfirmParam?: boolean;
}

interface IMcpServerConfig {
  authType?: McpAuthType;
  token?: string;
  sql?: IMcpSqlConfig;
}

export default function McpSetting() {
  const [storedConfig, setStoredConfig] = useState<IMcpServerConfig>({});
  const [mcpAuthType, setMcpAuthType] = useState<McpAuthType>('none');
  const [mcpToken, setMcpToken] = useState('');
  const [allowWrite, setAllowWrite] = useState(false);
  const [allowDdl, setAllowDdl] = useState(false);
  const [requireConfirmParam, setRequireConfirmParam] = useState(true);
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
        setStoredConfig(config);
        setMcpAuthType(config.authType || 'none');
        setMcpToken(config.token || '');
        setAllowWrite(Boolean(config.sql?.allowWrite));
        setAllowDdl(Boolean(config.sql?.allowDdl));
        setRequireConfirmParam(config.sql?.requireConfirmParam !== false);
      } catch {
        setStoredConfig({});
      }
    });
  }, []);

  async function saveMcpConfig() {
    await configServer.setSystemConfig({
      code: MCP_SERVER_CONFIG_CODE,
      content: JSON.stringify({
        ...storedConfig,
        authType: mcpAuthType,
        token: mcpAuthType === 'bearer' ? mcpToken.trim() : '',
        sql: {
          ...storedConfig.sql,
          allowWrite,
          allowDdl,
          requireConfirmParam,
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
      <div className={styles.title}>{i18n('setting.label.mcpRequireConfirm')}</div>
      <div className={classnames(styles.content, styles.chatGPTKey)}>
        <Switch checked={requireConfirmParam} onChange={setRequireConfirmParam} />
      </div>
      <div className={styles.bottomButton}>
        <Button type="primary" onClick={saveMcpConfig}>
          {i18n('setting.button.apply')}
        </Button>
      </div>
    </>
  );
}

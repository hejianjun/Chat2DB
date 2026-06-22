import React, { useEffect, useMemo, useState } from 'react';
import i18n from '@/i18n';
import { Button, Input, Select, message } from 'antd';
import classnames from 'classnames';
import styles from './index.less';
import configServer from '@/service/config';
import outSideService from '@/service/outside';

const MCP_ENDPOINT = '/api/mcp';
const MCP_SERVER_CONFIG_CODE = 'mcp.server.config';

type McpAuthType = 'none' | 'bearer';

interface IMcpServerConfig {
  authType: McpAuthType;
  token?: string;
}

// 代理设置
export default function ProxyBody() {
  const [apiPrefix, setApiPrefix] = useState(window._BaseURL);
  const [mcpAuthType, setMcpAuthType] = useState<McpAuthType>('none');
  const [mcpToken, setMcpToken] = useState('');
  const mcpAddress = useMemo(() => {
    return apiPrefix ? `${apiPrefix.replace(/\/+$/, '')}${MCP_ENDPOINT}` : '';
  }, [apiPrefix]);

  useEffect(() => {
    configServer.getSystemConfig({ code: MCP_SERVER_CONFIG_CODE }).then((res) => {
      if (!res?.content) {
        return;
      }
      try {
        const config = JSON.parse(res.content) as IMcpServerConfig;
        setMcpAuthType(config.authType || 'none');
        setMcpToken(config.token || '');
      } catch {
        setMcpAuthType('none');
        setMcpToken('');
      }
    });
  }, []);

  function updateApi(e: any) {
    setApiPrefix(e.target.value);
  }

  async function saveMcpConfig(targetApiPrefix: string) {
    const normalizedApiPrefix = targetApiPrefix.replace(/\/+$/, '');
    await configServer.setSystemConfigByUrl(`${normalizedApiPrefix}/api/config/system_config`, {
      data: {
        code: MCP_SERVER_CONFIG_CODE,
        content: JSON.stringify({
          authType: mcpAuthType,
          token: mcpAuthType === 'bearer' ? mcpToken.trim() : '',
        }),
      },
    });
  }

  async function affirmUpdateApi() {
    if (!apiPrefix) {
      return;
    }
    outSideService
      .dynamicUrl(`${apiPrefix}/api/system/get-version-a`)
      .then(async () => {
        await saveMcpConfig(apiPrefix);
        localStorage.setItem('_BaseURL', apiPrefix);
        location.reload();
      })
      .catch(() => {
        message.error(i18n('setting.message.urlTestError'));
      });
    // try {
    //   const xhr = new XMLHttpRequest();
    //   xhr.withCredentials = true;
    //   xhr.open('GET', `${apiPrefix}/api/system/get-version-a`);
    //   xhr.onload = function () {
    //     if (xhr.status === 200) {
    //       localStorage.setItem('_BaseURL', apiPrefix);
    //       location.reload();
    //     } else {
    //       message.error(i18n('setting.message.urlTestError'));
    //     }
    //   };
    //   xhr.send();
    // } catch {
    //   message.error(i18n('setting.message.urlTestError'));
    // }
  }

  return (
    <>
      <div className={styles.title}>{i18n('setting.label.serviceAddress')}</div>
      <div className={classnames(styles.content, styles.chatGPTKey)}>
        <Input value={apiPrefix} onChange={updateApi} />
      </div>
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
      <div className={styles.bottomButton}>
        <Button type="primary" onClick={affirmUpdateApi}>
          {i18n('setting.button.apply')}
        </Button>
      </div>
    </>
  );
}

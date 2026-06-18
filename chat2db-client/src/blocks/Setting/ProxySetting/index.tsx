import React, { useMemo, useState } from 'react';
import i18n from '@/i18n';
import { Button, Input, message } from 'antd';
import classnames from 'classnames';
import styles from './index.less';
import outSideService from '@/service/outside';

const MCP_SSE_PATH = '/api/mcp/sse';

// 代理设置
export default function ProxyBody() {
  const [apiPrefix, setApiPrefix] = useState(window._BaseURL);
  const mcpAddress = useMemo(() => {
    return apiPrefix ? `${apiPrefix.replace(/\/+$/, '')}${MCP_SSE_PATH}` : '';
  }, [apiPrefix]);

  function updateApi(e: any) {
    setApiPrefix(e.target.value);
  }

  function affirmUpdateApi() {
    if (!apiPrefix) {
      return;
    }
    outSideService
      .dynamicUrl(`${apiPrefix}/api/system/get-version-a`)
      .then(() => {
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
      <div className={styles.bottomButton}>
        <Button type="primary" onClick={affirmUpdateApi}>
          {i18n('setting.button.apply')}
        </Button>
      </div>
    </>
  );
}

import MonacoEditor from '@/components/MonacoEditor';
import React from 'react';
import styles from './index.less';

function Test() {
  return (
    <MonacoEditor id="0" className={styles.container} language="sql" defaultValue="select * from " />
  );
}

export default Test;

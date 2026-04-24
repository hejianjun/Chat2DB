/**
 * ER图图例组件
 * 显示真实外键和虚拟外键的图例说明
 */
import React, { memo } from 'react';
import i18n from '@/i18n';
import styles from './Legend.less';

const Legend = memo(() => {
  return (
    <div className={styles.legend}>
      <div className={styles.legendTitle}>{i18n('workspace.erDiagram.legend')}</div>
      {/* 真实外键：灰色实线 */}
      <div className={styles.legendItem}>
        <div className={`${styles.legendLine} ${styles.realLine}`} />
        <span>{i18n('workspace.erDiagram.realFk')}</span>
      </div>
      {/* 虚拟外键：橙色虚线 */}
      <div className={styles.legendItem}>
        <div className={`${styles.legendLine} ${styles.virtualLine}`} />
        <span>{i18n('workspace.erDiagram.virtualFk')}</span>
      </div>
    </div>
  );
});

export default Legend;

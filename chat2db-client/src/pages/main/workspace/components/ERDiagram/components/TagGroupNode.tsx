import React, { memo } from 'react';
import styles from './TagGroupNode.less';

interface IProps {
  data: { label: string };
}

export default memo<IProps>(({ data }) => (
  <div className={styles.group}>
    <span className={styles.label}>{data.label}</span>
  </div>
));

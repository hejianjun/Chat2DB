import React, { memo } from 'react';
import styles from './index.less';
import classnames from 'classnames';

interface IProps {
  className?: string;
}

export default memo((props: IProps) => {
  const { className } = props;
  return <div className={classnames(styles.spinner, styles.center, className)}>
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
    <div className={styles.spinnerBlade} />
  </div>

});

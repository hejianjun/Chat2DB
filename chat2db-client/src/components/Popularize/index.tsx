import React, { memo } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import i18n from '@/i18n';

interface IProps {
  className?: string;
}

export default memo<IProps>(function Popularize(props) {
  const { className } = props
  return <div className={classnames(styles.box, className)}>
    {/* <div className={styles.title}>获取更多次数</div> */}
    <div className={styles.wechatImg} />
    <div className={styles.text}>
      <p>{i18n('common.text.wechatPopularizeAi')}</p>
      <p>{i18n('common.text.wechatPopularize')}</p>
    </div>
  </div>
})

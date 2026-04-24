/**
 * ER图表名过滤组件
 * 提供表名搜索功能，支持模糊匹配过滤显示的表
 */
import React, { memo, useCallback } from 'react';
import { Input } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import i18n from '@/i18n';
import styles from './TableFilter.less';

interface ITableFilterProps {
  /** 当前过滤文本 */
  value: string;
  /** 过滤文本变更回调 */
  onChange: (value: string) => void;
}

const TableFilter = memo(({ value, onChange }: ITableFilterProps) => {
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      onChange(e.target.value);
    },
    [onChange],
  );

  return (
    <div className={styles.tableFilter}>
      <Input
        placeholder={i18n('workspace.erDiagram.filterPlaceholder')}
        prefix={<SearchOutlined />}
        value={value}
        onChange={handleChange}
        allowClear
        size="small"
        className={styles.filterInput}
      />
    </div>
  );
});

export default TableFilter;

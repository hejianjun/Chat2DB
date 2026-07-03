import React, { memo, useMemo } from 'react';
import classnames from 'classnames';

import { i18n } from '@/i18n';


import { useSchemaDiffStore, setSelectedTableDiffs, setDetailViewTableName } from '../store';
import DetailView from './DetailView';
import styles from './index.less';

/** 表差异类型 → i18n key 映射，用于在列表 Badge 上展示中文/英文文案。 */
const diffTypeLabel: Record<string, string> = {
  ADDED: 'schemaDiff.added',
  REMOVED: 'schemaDiff.removed',
  MODIFIED: 'schemaDiff.modified',
  UNCHANGED: 'schemaDiff.unchanged',
};

/** 表差异类型 → CSS class 映射，控制 Badge 颜色（绿/红/橙/灰）。 */
const diffTypeClass: Record<string, string> = {
  ADDED: styles.added,
  REMOVED: styles.removed,
  MODIFIED: styles.modified,
  UNCHANGED: styles.unchanged,
};

/**
 * 比较结果展示面板。
 *
 * 左侧为「有变化的表」列表，每行展示差异类型徽标、勾选框与列/索引/外键的差异计数；
 * 右侧根据 detailViewTableName 渲染对应表的明细（{@link DetailView}），未选择时占位。
 */
const ResultPanel: React.FC = memo(() => {
  const { compareResult, selectedTableDiffs, detailViewTableName } = useSchemaDiffStore();

  const tableDiffs = useMemo(() => compareResult?.tableDiffs || [], [compareResult]);

  // 已经在后端过滤过 UNCHANGED，这里再防御一次，确保面板上不会出现未变化的表。
  const visibleDiffs = useMemo(
    () => tableDiffs.filter((td) => td.diffType !== 'UNCHANGED'),
    [tableDiffs],
  );

  // 当前右侧详情对应的表对象，找不到时为 null（例如外部清空了 detailViewTableName）。
  const selectedTable = useMemo(
    () => visibleDiffs.find((td) => td.tableName === detailViewTableName) || null,
    [visibleDiffs, detailViewTableName],
  );

  /** 切换某张表的勾选状态，使用展开赋值确保 React 检测到引用变化。 */
  const handleToggleSelect = (tableName: string) => {
    setSelectedTableDiffs({
      ...selectedTableDiffs,
      [tableName]: !selectedTableDiffs[tableName],
    });
  };

  return (
    <div className={styles.resultPanel}>
      <div className={styles.tableList}>
        <div className={styles.listHeader}>
          {i18n('schemaDiff.table')}
          <span className={styles.count}>({visibleDiffs.length})</span>
        </div>
        {visibleDiffs.map((td) => {
          const ddlLength = td.ddlStatements?.length || (td.ddlStatement ? 1 : 0);
          return (
            <div
              key={td.tableName}
              className={classnames(styles.tableItem, {
                [styles.active]: detailViewTableName === td.tableName,
              })}
              onClick={() => setDetailViewTableName(td.tableName)}
            >
              <div className={styles.tableItemHeader}>
                <input
                  type="checkbox"
                  checked={!!selectedTableDiffs[td.tableName]}
                  onClick={(e) => e.stopPropagation()}
                  onChange={() => handleToggleSelect(td.tableName)}
                />
                <span className={classnames(styles.diffBadge, diffTypeClass[td.diffType])}>
                  {i18n(diffTypeLabel[td.diffType] as any)}
                </span>
                <span className={styles.tableItemName}>{td.tableName}</span>
              </div>
              <div className={styles.tableItemMeta}>
                {td.columnDiffs?.length ? <span>Col: {td.columnDiffs.length}</span> : null}
                {td.indexDiffs?.length ? <span>Idx: {td.indexDiffs.length}</span> : null}
                {td.foreignKeyDiffs?.length ? <span>FK: {td.foreignKeyDiffs.length}</span> : null}
                {ddlLength > 0 ? <span>DDL: {ddlLength} stmts</span> : null}
              </div>
            </div>
          );
        })}
        {visibleDiffs.length === 0 && (
          <div className={styles.noData}>{i18n('schemaDiff.noChanges')}</div>
        )}
      </div>
      <div className={styles.detailArea}>
        {selectedTable ? <DetailView tableDiff={selectedTable} /> : (
          <div className={styles.noSelection}>
            {i18n('schemaDiff.table')}
          </div>
        )}
      </div>
    </div>
  );
});

export default ResultPanel;

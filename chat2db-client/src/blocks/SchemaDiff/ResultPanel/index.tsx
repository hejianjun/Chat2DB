import React, { memo, useEffect, useMemo, useState } from 'react';
import { Checkbox } from 'antd';
import classnames from 'classnames';

import { i18n } from '@/i18n';
import { ITableDiff } from '@/typings/schemaDiff';

import { useSchemaDiffStore, setSelectedTableDiffs, setDetailViewTableName } from '../store';
import DetailView from './DetailView';
import styles from './index.less';

type DiffType = Exclude<ITableDiff['diffType'], 'UNCHANGED'>;
type DiffFilter = 'ALL' | DiffType;

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

const diffGroupOrder: DiffType[] = ['ADDED', 'REMOVED', 'MODIFIED'];

/**
 * 比较结果展示面板。
 *
 * 左侧为「有变化的表」列表，每行展示差异类型徽标、勾选框与列/索引/外键的差异计数；
 * 右侧根据 detailViewTableName 渲染对应表的明细（{@link DetailView}），未选择时占位。
 */
const ResultPanel: React.FC = memo(() => {
  const { compareResult, selectedTableDiffs, detailViewTableName } = useSchemaDiffStore();
  const [activeFilter, setActiveFilter] = useState<DiffFilter>('ALL');

  const tableDiffs = useMemo(() => compareResult?.tableDiffs || [], [compareResult]);

  useEffect(() => {
    setActiveFilter('ALL');
  }, [compareResult]);

  // 已经在后端过滤过 UNCHANGED，这里再防御一次，确保面板上不会出现未变化的表。
  const visibleDiffs = useMemo(() => tableDiffs.filter((td) => td.diffType !== 'UNCHANGED'), [tableDiffs]);

  const diffGroups = useMemo(
    () =>
      diffGroupOrder.map((diffType) => ({
        diffType,
        tableDiffs: visibleDiffs.filter((tableDiff) => tableDiff.diffType === diffType),
      })),
    [visibleDiffs],
  );

  const filteredGroups = useMemo(
    () => diffGroups.filter((group) => activeFilter === 'ALL' || group.diffType === activeFilter),
    [activeFilter, diffGroups],
  );

  const filteredDiffs = useMemo(() => filteredGroups.flatMap((group) => group.tableDiffs), [filteredGroups]);

  // 当前右侧详情对应的表对象，找不到时为 null（例如外部清空了 detailViewTableName）。
  const selectedTable = useMemo(
    () => filteredDiffs.find((td) => td.tableName === detailViewTableName) || null,
    [filteredDiffs, detailViewTableName],
  );

  /** 切换某张表的勾选状态，使用展开赋值确保 React 检测到引用变化。 */
  const handleToggleSelect = (tableName: string) => {
    setSelectedTableDiffs({
      ...selectedTableDiffs,
      [tableName]: !selectedTableDiffs[tableName],
    });
  };

  /** 单独全选或取消某个差异分组，不影响其他分组的勾选状态。 */
  const handleToggleGroup = (groupDiffs: ITableDiff[], checked: boolean) => {
    const nextSelectedTableDiffs = { ...selectedTableDiffs };
    groupDiffs.forEach((tableDiff) => {
      nextSelectedTableDiffs[tableDiff.tableName] = checked;
    });
    setSelectedTableDiffs(nextSelectedTableDiffs);
  };

  /** 只保留当前分组的表为选中状态，便于直接执行单一类型的迁移。 */
  const handleSelectOnlyGroup = (groupDiffs: ITableDiff[]) => {
    const nextSelectedTableDiffs = groupDiffs.reduce<Record<string, boolean>>((selected, tableDiff) => {
      selected[tableDiff.tableName] = true;
      return selected;
    }, {});
    setSelectedTableDiffs(nextSelectedTableDiffs);
  };

  /** 切换类型过滤；当前详情不在新分组中时，自动定位到该分组第一张表。 */
  const handleFilterChange = (filter: DiffFilter) => {
    setActiveFilter(filter);
    const nextVisibleDiffs =
      filter === 'ALL' ? visibleDiffs : visibleDiffs.filter((tableDiff) => tableDiff.diffType === filter);
    if (detailViewTableName && !nextVisibleDiffs.some((tableDiff) => tableDiff.tableName === detailViewTableName)) {
      setDetailViewTableName(nextVisibleDiffs[0]?.tableName || null);
    }
  };

  return (
    <div className={styles.resultPanel}>
      <div className={styles.tableList}>
        <div className={styles.listHeader}>
          <div className={styles.listTitle}>
            {i18n('schemaDiff.table')}
            <span className={styles.count}>({visibleDiffs.length})</span>
          </div>
          <div className={styles.filterList}>
            <button
              type="button"
              className={classnames(styles.filterButton, { [styles.active]: activeFilter === 'ALL' })}
              onClick={() => handleFilterChange('ALL')}
            >
              {i18n('schemaDiff.all')} {visibleDiffs.length}
            </button>
            {diffGroups.map((group) => (
              <button
                key={group.diffType}
                type="button"
                className={classnames(styles.filterButton, diffTypeClass[group.diffType], {
                  [styles.active]: activeFilter === group.diffType,
                })}
                onClick={() => handleFilterChange(group.diffType)}
              >
                {i18n(diffTypeLabel[group.diffType] as any)} {group.tableDiffs.length}
              </button>
            ))}
          </div>
        </div>
        {filteredGroups.map((group) => {
          if (group.tableDiffs.length === 0) {
            return null;
          }
          const selectedCount = group.tableDiffs.filter((tableDiff) => selectedTableDiffs[tableDiff.tableName]).length;
          return (
            <div key={group.diffType} className={styles.diffGroup}>
              <div className={styles.groupHeader}>
                <Checkbox
                  checked={selectedCount === group.tableDiffs.length}
                  indeterminate={selectedCount > 0 && selectedCount < group.tableDiffs.length}
                  onChange={(event) => handleToggleGroup(group.tableDiffs, event.target.checked)}
                />
                <span className={classnames(styles.groupLabel, diffTypeClass[group.diffType])}>
                  {i18n(diffTypeLabel[group.diffType] as any)}
                </span>
                <span className={styles.groupCount}>{group.tableDiffs.length}</span>
                <span className={styles.groupSelection}>{i18n('schemaDiff.selectedCount', selectedCount)}</span>
                <button
                  type="button"
                  className={styles.selectOnlyButton}
                  onClick={() => handleSelectOnlyGroup(group.tableDiffs)}
                >
                  {i18n('schemaDiff.selectOnlyGroup')}
                </button>
              </div>
              {group.tableDiffs.map((td) => {
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
                      <Checkbox
                        checked={!!selectedTableDiffs[td.tableName]}
                        onClick={(event) => event.stopPropagation()}
                        onChange={() => handleToggleSelect(td.tableName)}
                      />
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
            </div>
          );
        })}
        {filteredDiffs.length === 0 && <div className={styles.noData}>{i18n('schemaDiff.noChanges')}</div>}
      </div>
      <div className={styles.detailArea}>
        {selectedTable ? (
          <DetailView tableDiff={selectedTable} />
        ) : (
          <div className={styles.noSelection}>{i18n('schemaDiff.table')}</div>
        )}
      </div>
    </div>
  );
});

export default ResultPanel;

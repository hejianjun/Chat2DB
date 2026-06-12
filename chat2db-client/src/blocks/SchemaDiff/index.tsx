import React, { memo, useCallback, useEffect, useState } from 'react';
import { Button, Select, Spin, message, Modal, Checkbox } from 'antd';
import classnames from 'classnames';

import { i18n, i18nElement } from '@/i18n';
import connectionService from '@/service/connection';
import sqlService from '@/service/sql';
import { useConnectionStore, getConnectionList } from '@/pages/main/store/connection';
import { IConnectionListItem } from '@/typings/connection';
import { IDatabaseItem, ISchemaItem } from '@/typings';

import { useSchemaDiffStore, setSourceDataSource, setTargetDataSource, setSourceDatabase, setTargetDatabase, setSourceSchema, setTargetSchema, setCompareOption, setCompareResult, setComparing, setSelectedTableDiffs, setDetailViewTableName } from './store';
import ResultPanel from './ResultPanel';
import MigrationPanel from './MigrationPanel';
import styles from './index.less';

/**
 * SchemaDiffPanel - 表结构对比主面板。
 *
 * 提供完整的对比 → 预览 → 迁移闭环：
 * 1. 选择源 / 目标的数据源、数据库以及（可选）Schema；
 * 2. 配置比较选项（列、索引、外键、大小写敏感、忽略 MySQL 显示宽度等）；
 * 3. 触发比较，按表展示差异、改动详情与待执行 DDL；
 * 4. 勾选要执行的表，由 {@link MigrationPanel} 在目标库上执行 DDL 完成迁移。
 *
 * 组件本身只负责头部选择器、加载态与汇总条；详情和迁移分别交给 {@link ResultPanel}
 * 与 {@link MigrationPanel}，状态通过 useSchemaDiffStore 共享。
 */
const SchemaDiffPanel: React.FC = memo(() => {
  // Store state
  const {
    sourceDataSource, targetDataSource,
    sourceDatabase, targetDatabase,
    sourceSchema, targetSchema,
    compareOption, compareResult,
    comparing, selectedTableDiffs,
  } = useSchemaDiffStore();

  const connectionList = useConnectionStore((s) => s.connectionList);

  // 数据库 / Schema 列表与各自的加载态。这些数据依赖于上游选择，放本地 state 是为了
  // 避免污染全局 store；切换上游时下面的 useEffect 会负责清空与重新拉取。
  const [sourceDatabases, setSourceDatabases] = useState<IDatabaseItem[]>([]);
  const [targetDatabases, setTargetDatabases] = useState<IDatabaseItem[]>([]);
  const [sourceSchemas, setSourceSchemas] = useState<ISchemaItem[]>([]);
  const [targetSchemas, setTargetSchemas] = useState<ISchemaItem[]>([]);
  const [sourceDbLoading, setSourceDbLoading] = useState(false);
  const [targetDbLoading, setTargetDbLoading] = useState(false);
  const [sourceSchemaLoading, setSourceSchemaLoading] = useState(false);
  const [targetSchemaLoading, setTargetSchemaLoading] = useState(false);

  // 首次挂载时若全局连接列表还没初始化，则触发拉取。
  useEffect(() => {
    if (!connectionList) {
      getConnectionList();
    }
  }, [connectionList]);

  // 源数据源变化 → 拉源库列表。
  useEffect(() => {
    if (sourceDataSource?.id) {
      setSourceDbLoading(true);
      setSourceDatabases([]);
      connectionService.getDatabaseList({ dataSourceId: sourceDataSource.id })
        .then((res) => {
          setSourceDatabases(Array.isArray(res) ? res : []);
        })
        .catch(() => {
          message.error(i18n('schemaDiff.loadDatabaseFail'));
        })
        .finally(() => {
          setSourceDbLoading(false);
        });
    } else {
      setSourceDatabases([]);
    }
  }, [sourceDataSource?.id]);

  // 目标数据源变化 → 拉目标库列表。
  useEffect(() => {
    if (targetDataSource?.id) {
      setTargetDbLoading(true);
      setTargetDatabases([]);
      connectionService.getDatabaseList({ dataSourceId: targetDataSource.id })
        .then((res) => {
          setTargetDatabases(Array.isArray(res) ? res : []);
        })
        .catch(() => {
          message.error(i18n('schemaDiff.loadDatabaseFail'));
        })
        .finally(() => {
          setTargetDbLoading(false);
        });
    } else {
      setTargetDatabases([]);
    }
  }, [targetDataSource?.id]);

  // 源库选好后 → 拉源 Schema 列表（部分库不区分 schema，结果可能为空）。
  useEffect(() => {
    if (sourceDataSource?.id && sourceDatabase) {
      setSourceSchemaLoading(true);
      setSourceSchemas([]);
      connectionService.getSchemaList({ dataSourceId: sourceDataSource.id, databaseName: sourceDatabase })
        .then((res) => {
          setSourceSchemas(Array.isArray(res) ? res : []);
        })
        .catch(() => {
          message.error(i18n('schemaDiff.loadSchemaFail'));
        })
        .finally(() => {
          setSourceSchemaLoading(false);
        });
    } else {
      setSourceSchemas([]);
    }
  }, [sourceDataSource?.id, sourceDatabase]);

  // 目标库选好后 → 拉目标 Schema 列表。
  useEffect(() => {
    if (targetDataSource?.id && targetDatabase) {
      setTargetSchemaLoading(true);
      setTargetSchemas([]);
      connectionService.getSchemaList({ dataSourceId: targetDataSource.id, databaseName: targetDatabase })
        .then((res) => {
          setTargetSchemas(Array.isArray(res) ? res : []);
        })
        .catch(() => {
          message.error(i18n('schemaDiff.loadSchemaFail'));
        })
        .finally(() => {
          setTargetSchemaLoading(false);
        });
    } else {
      setTargetSchemas([]);
    }
  }, [targetDataSource?.id, targetDatabase]);

  /**
   * 触发后端 /api/rdb/schema/diff/compare：
   * 1. 必填项校验（缺源/目标的库则直接提示）；
   * 2. 调用接口写回 compareResult；
   * 3. 默认勾选所有「非未变化」的表，方便用户一键全选迁移。
   */
  const handleCompare = useCallback(async () => {
    if (!sourceDataSource || !targetDataSource || !sourceDatabase || !targetDatabase) {
      message.warning(i18n('schemaDiff.selectSource'));
      return;
    }
    setComparing(true);
    try {
      const result = await sqlService.compareSchema({
        sourceDataSourceId: sourceDataSource.id,
        sourceDatabaseName: sourceDatabase,
        sourceSchemaName: sourceSchema || undefined,
        targetDataSourceId: targetDataSource.id,
        targetDatabaseName: targetDatabase,
        targetSchemaName: targetSchema || undefined,
        compareOption,
      });
      setCompareResult(result);
      if (result?.tableDiffs) {
        const selected: Record<string, boolean> = {};
        result.tableDiffs.forEach((td) => {
          if (td.diffType !== 'UNCHANGED') {
            selected[td.tableName] = true;
          }
        });
        setSelectedTableDiffs(selected);
      }
    } catch (e: any) {
      message.error(e?.message || 'Compare failed');
    } finally {
      setComparing(false);
    }
  }, [sourceDataSource, targetDataSource, sourceDatabase, targetDatabase, sourceSchema, targetSchema, compareOption]);

  // 头部汇总条用：把新增 / 删除 / 修改的表数加起来，用于展示「共 N 处变更」。
  const totalChanges = compareResult
    ? (compareResult.summary?.tablesOnlyInSource || 0)
    + (compareResult.summary?.tablesOnlyInTarget || 0)
    + (compareResult.summary?.modifiedTables || 0)
    : 0;

  // 渲染：顶部选择器 + 比较选项 + 加载态 + 汇总条 + 详情/迁移面板。
  return (
    <div className={styles.schemaDiffPanel}>
      <div className={styles.header}>
        <div className={styles.sourceSelector}>
          <div className={styles.selectorLabel}>{i18n('schemaDiff.source')}</div>
          <Select
            showSearch
            placeholder={i18n('schemaDiff.selectSource')}
            value={sourceDataSource?.id}
            onChange={(id) => {
              const ds = connectionList?.find((c: IConnectionListItem) => c.id === id) || null;
              setSourceDataSource(ds ? { id: ds.id!, alias: ds.alias || '', dbType: ds.type || '' } : null);
            }}
            style={{ width: 200 }}
            optionFilterProp="label"
            options={(connectionList || []).map((c: IConnectionListItem) => ({
              label: c.alias,
              value: c.id,
            }))}
          />
          <Select
            placeholder={i18n('schemaDiff.selectDatabase')}
            value={sourceDatabase || undefined}
            onChange={(v) => setSourceDatabase(v || '')}
            style={{ width: 160, marginLeft: 8 }}
            loading={sourceDbLoading}
            options={(sourceDatabases || []).map((db: IDatabaseItem) => ({ label: db.name, value: db.name }))}
          />
          {sourceSchemas.length > 0 && (
            <Select
              placeholder={i18n('schemaDiff.selectSchema')}
              value={sourceSchema || undefined}
              onChange={(v) => setSourceSchema(v || '')}
              style={{ width: 140, marginLeft: 8 }}
              loading={sourceSchemaLoading}
              options={(sourceSchemas || []).map((s: ISchemaItem) => ({ label: s.name, value: s.name }))}
              allowClear
            />
          )}
        </div>
        <div className={styles.selectorArrow}>→</div>
        <div className={styles.targetSelector}>
          <div className={styles.selectorLabel}>{i18n('schemaDiff.target')}</div>
          <Select
            showSearch
            placeholder={i18n('schemaDiff.selectTarget')}
            value={targetDataSource?.id}
            onChange={(id) => {
              const ds = connectionList?.find((c: IConnectionListItem) => c.id === id) || null;
              setTargetDataSource(ds ? { id: ds.id!, alias: ds.alias || '', dbType: ds.type || '' } : null);
            }}
            style={{ width: 200 }}
            optionFilterProp="label"
            options={(connectionList || []).map((c: IConnectionListItem) => ({
              label: c.alias,
              value: c.id,
            }))}
          />
          <Select
            placeholder={i18n('schemaDiff.selectDatabase')}
            value={targetDatabase || undefined}
            onChange={(v) => setTargetDatabase(v || '')}
            style={{ width: 160, marginLeft: 8 }}
            loading={targetDbLoading}
            options={(targetDatabases || []).map((db: IDatabaseItem) => ({ label: db.name, value: db.name }))}
          />
          {targetSchemas.length > 0 && (
            <Select
              placeholder={i18n('schemaDiff.selectSchema')}
              value={targetSchema || undefined}
              onChange={(v) => setTargetSchema(v || '')}
              style={{ width: 140, marginLeft: 8 }}
              loading={targetSchemaLoading}
              options={(targetSchemas || []).map((s: ISchemaItem) => ({ label: s.name, value: s.name }))}
              allowClear
            />
          )}
        </div>
        <div className={styles.compareActions}>
          <Button type="primary" onClick={handleCompare} loading={comparing}>
            {comparing ? i18n('schemaDiff.comparing') : i18n('schemaDiff.compare')}
          </Button>
        </div>
      </div>

      <div className={styles.options}>
        <Checkbox checked={compareOption.compareColumn} onChange={(e) => setCompareOption({ ...compareOption, compareColumn: e.target.checked })}>
          {i18n('schemaDiff.compareColumn')}
        </Checkbox>
        <Checkbox checked={compareOption.compareIndex} onChange={(e) => setCompareOption({ ...compareOption, compareIndex: e.target.checked })}>
          {i18n('schemaDiff.compareIndex')}
        </Checkbox>
        <Checkbox checked={compareOption.compareForeignKey} onChange={(e) => setCompareOption({ ...compareOption, compareForeignKey: e.target.checked })}>
          {i18n('schemaDiff.compareForeignKey')}
        </Checkbox>
        <Checkbox checked={compareOption.compareTableOption} onChange={(e) => setCompareOption({ ...compareOption, compareTableOption: e.target.checked })}>
          {i18n('schemaDiff.compareTableOption')}
        </Checkbox>
        <Checkbox checked={compareOption.excludeDeprecated} onChange={(e) => setCompareOption({ ...compareOption, excludeDeprecated: e.target.checked })}>
          {i18n('schemaDiff.excludeDeprecated')}
        </Checkbox>
        <Checkbox checked={compareOption.caseSensitive} onChange={(e) => setCompareOption({ ...compareOption, caseSensitive: e.target.checked })}>
          {i18n('schemaDiff.caseSensitive')}
        </Checkbox>
        <Checkbox checked={compareOption.ignoreCharsetAlias} onChange={(e) => setCompareOption({ ...compareOption, ignoreCharsetAlias: e.target.checked })}>
          {i18n('schemaDiff.ignoreCharsetAlias')}
        </Checkbox>
        <Checkbox checked={compareOption.ignoreIntegerDisplayWidth} onChange={(e) => setCompareOption({ ...compareOption, ignoreIntegerDisplayWidth: e.target.checked })}>
          {i18n('schemaDiff.ignoreIntegerDisplayWidth')}
        </Checkbox>
        <Checkbox checked={compareOption.ignoreAutoIncrement} onChange={(e) => setCompareOption({ ...compareOption, ignoreAutoIncrement: e.target.checked })}>
          {i18n('schemaDiff.ignoreAutoIncrement')}
        </Checkbox>
      </div>

      {comparing && (
        <div className={styles.loadingContainer}>
          <Spin size="large" />
        </div>
      )}

      {compareResult && (
        <>
          <div className={styles.summaryBar}>
            <span className={styles.summaryItem}>
              {i18n('schemaDiff.totalTables')}: <strong>{compareResult.summary?.totalTables || 0}</strong>
            </span>
            <span className={classnames(styles.summaryItem, styles.added)}>
              +{i18n('schemaDiff.tablesAdded')}: <strong>{compareResult.summary?.tablesOnlyInSource || 0}</strong>
            </span>
            <span className={classnames(styles.summaryItem, styles.removed)}>
              -{i18n('schemaDiff.tablesRemoved')}: <strong>{compareResult.summary?.tablesOnlyInTarget || 0}</strong>
            </span>
            <span className={classnames(styles.summaryItem, styles.modified)}>
              ~{i18n('schemaDiff.tablesModified')}: <strong>{compareResult.summary?.modifiedTables || 0}</strong>
            </span>
            <span className={classnames(styles.summaryItem, styles.unchanged)}>
              {i18n('schemaDiff.tablesUnchanged')}: <strong>{compareResult.summary?.unchangedTables || 0}</strong>
            </span>
            {compareResult.summary?.excludedDeprecatedTables > 0 && (
              <span className={classnames(styles.summaryItem, styles.excluded)}>
                {i18n('schemaDiff.excluded')}: <strong>{compareResult.summary.excludedDeprecatedTables}</strong>
              </span>
            )}
          </div>

          <ResultPanel />
          <MigrationPanel />
        </>
      )}
    </div>
  );
});

export default SchemaDiffPanel;

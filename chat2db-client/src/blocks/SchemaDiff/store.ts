import { createWithEqualityFn } from 'zustand/traditional';
import { devtools } from 'zustand/middleware';
import { shallow } from 'zustand/shallow';

import {
  ISchemaDiffResult,
  ITableDiff,
  ICompareOption,
  IMigrateResult,
} from '@/typings/schemaDiff';

/** 数据源下拉选项的精简结构（id + 别名 + 数据库类型）。 */
export interface IConnectionOption {
  id: number;
  alias: string;
  dbType: string;
}

/**
 * SchemaDiff 模块的 Zustand 状态结构。
 *
 * 状态分为三段：
 * 1. 源/目标选择：sourceDataSource、sourceDatabase、sourceSchema 及 target 同名字段；
 * 2. 比较：compareOption（比较选项）、compareResult（结果）、comparing（加载中）；
 * 3. 迁移：selectedTableDiffs（表勾选）、selectedStatementIndexes（语句勾选）、
 *    detailViewTableName（右侧详情）、migrationExecuting / migrationResult。
 */
export interface ISchemaDiffStore {
  sourceDataSource: IConnectionOption | null;
  targetDataSource: IConnectionOption | null;
  sourceDatabase: string;
  targetDatabase: string;
  sourceSchema: string;
  targetSchema: string;
  compareOption: ICompareOption;
  compareResult: ISchemaDiffResult | null;
  selectedTableDiffs: Record<string, boolean>;
  selectedStatementIndexes: Record<number, boolean>;
  detailViewTableName: string | null;
  comparing: boolean;
  migrationExecuting: boolean;
  migrationResult: IMigrateResult | null;
}

/** 默认比较选项：开启全部对比项，忽略 MySQL 显示宽度/字符集别名/自增起始值，排除废弃表。 */
const defaultCompareOption: ICompareOption = {
  compareColumn: true,
  compareIndex: true,
  compareForeignKey: true,
  compareTableOption: true,
  caseSensitive: false,
  excludeDeprecated: true,
  ignoreCharsetAlias: true,
  ignoreIntegerDisplayWidth: true,
  ignoreAutoIncrement: true,
};

/** Store 的初始值，用于打开面板与 resetStore 时复用。 */
export const initSchemaDiffStore = {
  sourceDataSource: null,
  targetDataSource: null,
  sourceDatabase: '',
  targetDatabase: '',
  sourceSchema: '',
  targetSchema: '',
  compareOption: defaultCompareOption,
  compareResult: null,
  selectedTableDiffs: {},
  selectedStatementIndexes: {},
  detailViewTableName: null,
  comparing: false,
  migrationExecuting: false,
  migrationResult: null,
};

export const useSchemaDiffStore = createWithEqualityFn<ISchemaDiffStore>()(
  devtools(() => initSchemaDiffStore),
  shallow,
);

// 下面这些 setter 在切换上游条件时会主动清空下游派生状态（库/Schema/比较结果），
// 避免出现「数据源换了但还显示老库列表」之类的脏数据。

export const setSourceDataSource = (sourceDataSource: IConnectionOption | null) =>
  useSchemaDiffStore.setState({ sourceDataSource, sourceDatabase: '', sourceSchema: '', compareResult: null });

export const setTargetDataSource = (targetDataSource: IConnectionOption | null) =>
  useSchemaDiffStore.setState({ targetDataSource, targetDatabase: '', targetSchema: '', compareResult: null });

export const setSourceDatabase = (sourceDatabase: string) =>
  useSchemaDiffStore.setState({ sourceDatabase, sourceSchema: '', compareResult: null });

export const setTargetDatabase = (targetDatabase: string) =>
  useSchemaDiffStore.setState({ targetDatabase, targetSchema: '', compareResult: null });

export const setSourceSchema = (sourceSchema: string) =>
  useSchemaDiffStore.setState({ sourceSchema, compareResult: null });

export const setTargetSchema = (targetSchema: string) =>
  useSchemaDiffStore.setState({ targetSchema, compareResult: null });

// 比较选项变化也会让旧结果失效，强制用户重新比较，避免选项与结果不匹配。
export const setCompareOption = (compareOption: ICompareOption) =>
  useSchemaDiffStore.setState({ compareOption, compareResult: null });

// 写入新比较结果时，连带重置勾选与详情视图，防止残留旧表的勾选状态。
export const setCompareResult = (compareResult: ISchemaDiffResult | null) =>
  useSchemaDiffStore.setState({ compareResult, selectedTableDiffs: {}, selectedStatementIndexes: {}, detailViewTableName: null });

export const setComparing = (comparing: boolean) =>
  useSchemaDiffStore.setState({ comparing });

export const setSelectedTableDiffs = (selectedTableDiffs: Record<string, boolean>) =>
  useSchemaDiffStore.setState({ selectedTableDiffs });

export const setSelectedStatementIndexes = (selectedStatementIndexes: Record<number, boolean>) =>
  useSchemaDiffStore.setState({ selectedStatementIndexes });

export const setDetailViewTableName = (detailViewTableName: string | null) =>
  useSchemaDiffStore.setState({ detailViewTableName });

export const setMigrationExecuting = (migrationExecuting: boolean) =>
  useSchemaDiffStore.setState({ migrationExecuting });

export const setMigrationResult = (migrationResult: IMigrateResult | null) =>
  useSchemaDiffStore.setState({ migrationResult });

/** 关闭面板或需要彻底清空时调用，把 store 还原成初始状态。 */
export const resetStore = () =>
  useSchemaDiffStore.setState({ ...initSchemaDiffStore });

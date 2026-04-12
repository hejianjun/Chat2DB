import { ConsoleStatus, DatabaseTypeCode, WorkspaceTabType, ConsoleOpenedStatus } from '@/constants';
import { IConnectionEnv } from './connection';

export interface ICreateConsoleParams { 
  name?: string;
  ddl?: string;
  dataSourceId: number;
  dataSourceName: string;
  databaseType: DatabaseTypeCode;
  databaseName?: string;
  schemaName?: string;
  operationType?: WorkspaceTabType;
  loadSQL?: () => Promise<string>;
}

// 控制台详情
export interface IConsole {
  id: number; // consoleId
  name: string; // 控制台名称
  ddl: string; // 控制台内的 sql
  dataSourceId?: number; // 数据源 id
  dataSourceName?: string; // 数据源名称
  type?: DatabaseTypeCode; // 数据库类型
  databaseName?: string; // 数据库名称
  schemaName?: string; // schema 名称
  status: ConsoleStatus; // 控制台状态
  connectable: boolean; // 是否可连接
  tabOpened?: ConsoleOpenedStatus; // 控制台 tab 是否打开
  operationType: WorkspaceTabType; // 操作类型
  environment?: IConnectionEnv; // 环境信息
}

export type ICreateConsole = Omit<IConsole, 'id' | 'dataSourceName' | 'connectable'>;


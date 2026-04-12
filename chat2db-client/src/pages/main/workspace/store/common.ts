import { IConnectionListItem } from '@/typings/connection';
import { useWorkspaceStore } from './index';

export type IAiChatPromptType = 'NL_2_SQL' | 'SQL_EXPLAIN' | 'SQL_OPTIMIZER' | 'SQL_2_SQL' | 'NL_2_COMMENT';

export interface IColumnComment {
  column_name: string;
  comment: string;
}

export interface ITableCommentResult {
  table_comment: string;
  column_comments: IColumnComment[];
}

export interface IPendingAiChat {
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string | null;
  tableNames?: string[] | null;
  message: string;
  promptType: IAiChatPromptType;
  onCommentGenerated?: (result: ITableCommentResult) => void;
}

export interface ICommonStore {
  currentConnectionDetails: IConnectionListItem | null;
  currentWorkspaceExtend: string | null;
  currentWorkspaceGlobalExtend: {
    code: string,
    uniqueData: any,
  } | null;
  pendingAiChat: IPendingAiChat | null;
}

export const initCommonStore: ICommonStore = {
  currentConnectionDetails: null,
  currentWorkspaceExtend: null,
  currentWorkspaceGlobalExtend: null,
  pendingAiChat: null,
}

export const setCurrentConnectionDetails = (connectionDetails: ICommonStore['currentConnectionDetails']) => {
  return useWorkspaceStore.setState({ currentConnectionDetails: connectionDetails });
}

export const setCurrentWorkspaceExtend = (workspaceExtend: ICommonStore['currentWorkspaceExtend']) => {
  return useWorkspaceStore.setState({ currentWorkspaceExtend: workspaceExtend });
}

export const setCurrentWorkspaceGlobalExtend = (workspaceGlobalExtend: ICommonStore['currentWorkspaceGlobalExtend']) => {
  return useWorkspaceStore.setState({ currentWorkspaceGlobalExtend: workspaceGlobalExtend });
}

export const setPendingAiChat = (pendingAiChat: ICommonStore['pendingAiChat']) => {
  return useWorkspaceStore.setState({ pendingAiChat });
}

import { IConnectionListItem } from '@/typings/connection';
import { useWorkspaceStore } from './index';

export type IAiChatPromptType =
  | 'NL_2_SQL'
  | 'SQL_EXPLAIN'
  | 'SQL_OPTIMIZER'
  | 'SQL_2_SQL'
  | 'TITLE_GENERATION'
  | 'SELECT_TABLES'
  | 'NL_2_COMMENT'
  | 'NL_2_COMMENT_BATCH'
  | 'REDIS_KEYS_COMMENT'
  | 'NL_2_FIELD_MAPPING'
  | 'NL_2_DATA_EXPRESSION'
  | 'SQL_FIX'
  | 'SQL_COMPLETION';

export const SIMPLE_TASK_PROMPT_TYPES = [
  'TITLE_GENERATION',
  'SELECT_TABLES',
  'NL_2_COMMENT',
  'NL_2_COMMENT_BATCH',
  'REDIS_KEYS_COMMENT',
  'NL_2_FIELD_MAPPING',
  'NL_2_DATA_EXPRESSION',
  'SQL_COMPLETION',
] as const;

export const isSimpleTaskPromptType = (promptType?: string | null): boolean => {
  return SIMPLE_TASK_PROMPT_TYPES.includes(promptType as (typeof SIMPLE_TASK_PROMPT_TYPES)[number]);
};

export const CONVERSATIONAL_SQL_PROMPT_TYPES = [
  'NL_2_SQL',
  'SQL_EXPLAIN',
  'SQL_OPTIMIZER',
  'SQL_2_SQL',
] as const;

export const isConversationalSqlPromptType = (promptType?: string | null): boolean => {
  return CONVERSATIONAL_SQL_PROMPT_TYPES.includes(
    promptType as (typeof CONVERSATIONAL_SQL_PROMPT_TYPES)[number],
  );
};

export interface IColumnComment {
  column_name: string;
  comment: string;
}

export interface ITableCommentResult {
  table_comment: string;
  column_comments: IColumnComment[];
}

export interface IBatchTableComment {
  table_name: string;
  table_comment: string;
}

export interface IBatchTableCommentResult {
  tables: IBatchTableComment[];
}

export interface IRedisKeyPrefixComment {
  prefix: string;
  comment: string;
}

export interface IRedisKeyPrefixCommentResult {
  prefixes: IRedisKeyPrefixComment[];
}

export interface IFieldMappingResult {
  mappings: {
    sourceField: string;
    targetField: string;
    confidence: number;
  }[];
}

export interface IDataExpressionResult {
  column_expressions: {
    column_name: string;
    expression: string;
    reason: string;
  }[];
}

export interface ISqlFixResult {
  error_analysis: string;
  fixed_sql: string;
  explanation: string;
  can_fix: boolean;
}

export interface IPendingAiChat {
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string | null;
  tableNames?: string[] | null;
  message: string;
  promptType: IAiChatPromptType;
  onCommentGenerated?: (result: ITableCommentResult) => void;
  onBatchCommentGenerated?: (result: IBatchTableCommentResult) => void;
  onRedisKeyPrefixCommentGenerated?: (result: IRedisKeyPrefixCommentResult) => void;
  onMappingGenerated?: (result: IFieldMappingResult) => void;
  onExpressionGenerated?: (result: IDataExpressionResult) => void;
  onSqlFixed?: (sql: string) => void;
  ext?: string;
}

export interface ICommonStore {
  currentConnectionDetails: IConnectionListItem | null;
  currentWorkspaceExtend: string | null;
  currentWorkspaceGlobalExtend: {
    code: string;
    uniqueData: any;
  } | null;
  pendingAiChat: IPendingAiChat | null;
}

export const initCommonStore: ICommonStore = {
  currentConnectionDetails: null,
  currentWorkspaceExtend: null,
  currentWorkspaceGlobalExtend: null,
  pendingAiChat: null,
};

export const setCurrentConnectionDetails = (connectionDetails: ICommonStore['currentConnectionDetails']) => {
  return useWorkspaceStore.setState({ currentConnectionDetails: connectionDetails });
};

export const setCurrentWorkspaceExtend = (workspaceExtend: ICommonStore['currentWorkspaceExtend']) => {
  return useWorkspaceStore.setState({ currentWorkspaceExtend: workspaceExtend });
};

export const setCurrentWorkspaceGlobalExtend = (
  workspaceGlobalExtend: ICommonStore['currentWorkspaceGlobalExtend'],
) => {
  return useWorkspaceStore.setState({ currentWorkspaceGlobalExtend: workspaceGlobalExtend });
};

export const setPendingAiChat = (pendingAiChat: ICommonStore['pendingAiChat']) => {
  return useWorkspaceStore.setState({ pendingAiChat });
};

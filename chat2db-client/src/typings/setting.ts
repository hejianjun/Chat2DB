import { AIType } from './ai';

export enum DateTimeFormat {
  DATE_TIME = 'YYYY-MM-DD HH:mm:ss',
  DATE_TIME_MILLIS = 'YYYY-MM-DD HH:mm:ss.SSS',
  DATE_TIME_SLASH = 'YYYY/MM/DD HH:mm:ss',
  ISO_8601 = 'ISO_8601',
}

export const SYSTEM_TIME_ZONE = 'SYSTEM';

export interface IDateTimeDisplayConfig {
  format: DateTimeFormat;
  timeZone: string;
}

export interface IAnthropicConfig {
  aiSqlSource: AIType.ANTHROPIC;
  apiKey: string;
  apiHost?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  topK?: number;
  stopSequences?: string;
  betaVersion?: string;
}

export interface IOpenAIConfig {
  aiSqlSource: AIType.OPENAI;
  apiKey: string;
  apiHost?: string;
  httpProxyHost?: string;
  httpProxyPort?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  n?: number;
  stop?: string;
  presencePenalty?: number;
  frequencyPenalty?: number;
  logitBias?: string;
  user?: string;
  organizationId?: string;
  projectId?: string;
}

export interface IFastAIConfig {
  aiSqlSource?: AIType;
  apiKey?: string;
  apiHost?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
}

export type IAiConfig = IAnthropicConfig | IOpenAIConfig;

export interface IModelItem {
  id?: string;
  name: string;
  model: string;
  contextWindowTokens?: number;
}

export interface IModelServiceConfig {
  id?: string;
  name: string;
  provider: AIType;
  apiKey?: string;
  apiHost?: string;
  completionsPath?: string;
  httpProxyHost?: string;
  httpProxyPort?: string;
  organizationId?: string;
  projectId?: string;
  modelList: IModelItem[];
}

export interface IDefaultModelConfig {
  defaultModelId: string;
  fastModelId?: string;
}

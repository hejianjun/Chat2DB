import { AIType } from './ai';

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
}

export interface IModelServiceConfig {
  id?: string;
  name: string;
  provider: AIType;
  apiKey?: string;
  apiHost?: string;
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

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

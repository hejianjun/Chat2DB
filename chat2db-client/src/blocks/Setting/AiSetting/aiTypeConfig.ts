import i18n from '@/i18n';
import { AIType } from '@/typings/ai';

export type IAiConfigBooleans = {
  [key: string]: boolean | string | number;
};

const AITypeName = {
  [AIType.ANTHROPIC]: 'Anthropic',
  [AIType.OPENAI]: 'OpenAI',
};

const AIFormConfig: Record<AIType, IAiConfigBooleans> = {
  [AIType.ANTHROPIC]: {
    apiKey: true,
    apiHost: 'https://api.anthropic.com',
    model: 'claude-3-5-sonnet-20241022',
    temperature: 0.7,
    maxTokens: 4096,
    topP: 0.999,
    topK: 5,
    stopSequences: '',
    betaVersion: 'tools-2024-05-16',
  },
  [AIType.OPENAI]: {
    apiKey: true,
    apiHost: 'https://api.openai.com/',
    httpProxyHost: '',
    httpProxyPort: '',
    model: 'gpt-4o-mini',
    temperature: 0.7,
    maxTokens: 4096,
    topP: 1,
    n: 1,
    stop: '',
    presencePenalty: 0,
    frequencyPenalty: 0,
    logitBias: '',
    user: '',
    organizationId: '',
    projectId: '',
  },
};

export { AIFormConfig, AITypeName };


import React, { useEffect, useState } from 'react';
import configService from '@/service/config';
import { AIType } from '@/typings/ai';
import { Alert, Button, Form, Input, Radio, RadioChangeEvent, InputNumber, Divider, Typography } from 'antd';
import i18n from '@/i18n';
import { IAiConfig, IFastAIConfig } from '@/typings/setting';
import { IRole } from '@/typings/user'
import { AIFormConfig, AITypeName, FastAIFormConfig } from './aiTypeConfig';
import styles from './index.less';
import { useUserStore } from '@/store/user'

interface IProps {
  handleApplyAiConfig: (aiConfig: IAiConfig) => void;
  aiConfig: IAiConfig;
}

function capitalizeFirstLetter(string) {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

// 转换参数名以更好地显示
function formatParamName(param: string): string {
  // 特殊参数名映射
  const nameMap: Record<string, string> = {
    'apiHost': 'API Host',
    'httpProxyHost': 'HTTP Proxy Host',
    'httpProxyPort': 'HTTP Proxy Port',
    'maxTokens': 'Max Tokens',
    'topP': 'Top P',
    'topK': 'Top K',
    'stopSequences': 'Stop Sequences',
    'betaVersion': 'Beta Version',
    'presencePenalty': 'Presence Penalty',
    'frequencyPenalty': 'Frequency Penalty',
    'logitBias': 'Logit Bias',
    'organizationId': 'Organization ID',
    'projectId': 'Project ID',
  };
  
  if (nameMap[param]) {
    return nameMap[param];
  }
  
  // 将驼峰命名转换为带空格的格式
  return param.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase());
}

// 确定输入类型
function getFieldType(key: string): 'input' | 'number' {
  const numberFields = [
    'temperature', 'maxTokens', 'topP', 'topK', 'n', 
    'presencePenalty', 'frequencyPenalty', 'httpProxyPort'
  ];
  return numberFields.includes(key) ? 'number' : 'input';
}

// openAI 的设置项
export default function SettingAI(props: IProps) {
  const [aiConfig, setAiConfig] = useState<IAiConfig>();
  const [fastAiConfig, setFastAiConfig] = useState<IFastAIConfig>();
  const { userInfo } = useUserStore(state => {
    return {
      userInfo: state.curUser
    }
  })

  useEffect(() => {
    setAiConfig(props.aiConfig);
    loadFastAiConfig();
  }, [props.aiConfig]);

  const loadFastAiConfig = async () => {
    const res = await configService.getFastAiSystemConfig({
      aiSqlSource: aiConfig?.aiSqlSource,
    });
    setFastAiConfig(res);
  };

  if (!aiConfig) {
    return <Alert description={i18n('setting.ai.tips')} type="warning" showIcon />;
  }

  if (userInfo?.roleCode && userInfo?.roleCode === IRole.USER) {
    // 如果是用户，不能配置ai
    return <Alert description={i18n('setting.ai.user.hidden')} type="warning" showIcon />;
  }

  const handleAiTypeChange = async (e: RadioChangeEvent) => {
    const aiSqlSource = e.target.value;

    // 查询对应 ai 类型的配置
    const res = await configService.getAiSystemConfig({
      aiSqlSource,
    });
    setAiConfig(res);
    
    // 同时加载快速模型配置
    const fastRes = await configService.getFastAiSystemConfig({
      aiSqlSource,
    });
    setFastAiConfig(fastRes);
  };

  /** 应用快速 Ai 配置 */
  const handleApplyFastAiConfig = async () => {
    const newFastAiConfig = { ...fastAiConfig };
    if (newFastAiConfig.apiHost && !newFastAiConfig.apiHost?.endsWith('/')) {
      newFastAiConfig.apiHost = newFastAiConfig.apiHost + '/';
    }
    
    await configService.setFastAiSystemConfig(newFastAiConfig as any);
    
    // 刷新配置
    await loadFastAiConfig();
  };

  /** 应用 Ai 配置 */
  const handleApplyAiConfig = () => {
    const newAiConfig = { ...aiConfig };
    if (newAiConfig.apiHost && !newAiConfig.apiHost?.endsWith('/')) {
      newAiConfig.apiHost = newAiConfig.apiHost + '/';
    }
    
    if (props.handleApplyAiConfig) {
      props.handleApplyAiConfig(newAiConfig);
    }
  };

  return (
    <>
      <div className={styles.aiSqlSource}>
        <div className={styles.aiSqlSourceTitle}>{i18n('setting.title.aiSource')}:</div>
        <Radio.Group onChange={handleAiTypeChange} value={aiConfig?.aiSqlSource}>
          {Object.keys(AIType).map((key) => (
            <Radio key={key} value={AIType[key]} style={{ marginBottom: '8px' }}>
              {AITypeName[AIType[key]]}
            </Radio>
          ))}
        </Radio.Group>
      </div>

      <Divider orientation="left">主模型配置</Divider>

      <Form layout="vertical">
        {Object.keys(AIFormConfig[aiConfig?.aiSqlSource]).map((key: string) => {
          const fieldType = getFieldType(key);
          const isRequired = key === 'apiKey';
          
          return (
            <Form.Item
              key={key}
              required={isRequired}
              label={formatParamName(key)}
              className={styles.title}
            >
              {fieldType === 'number' ? (
                <InputNumber
                  style={{ width: '100%' }}
                  value={aiConfig[key as keyof IAiConfig] as number}
                  placeholder={AIFormConfig[aiConfig?.aiSqlSource]?.[key] as string}
                  onChange={(value) => {
                    setAiConfig({ ...aiConfig, [key]: value });
                  }}
                  min={key === 'temperature' || key === 'topP' ? 0 : undefined}
                  max={key === 'temperature' || key === 'topP' ? 1 : undefined}
                  step={key === 'temperature' || key === 'topP' ? 0.1 : 1}
                />
              ) : (
                <Input
                  autoComplete="off"
                  value={aiConfig[key as keyof IAiConfig] as string}
                  placeholder={AIFormConfig[aiConfig?.aiSqlSource]?.[key] as string}
                  onChange={(e) => {
                    setAiConfig({ ...aiConfig, [key]: e.target.value });
                  }}
                />
              )}
            </Form.Item>
          );
        })}
      </Form>

      <div className={styles.bottomButton}>
        <Button type="primary" onClick={handleApplyAiConfig}>
          {i18n('setting.button.apply')}
        </Button>
      </div>

      <Divider orientation="left">快速模型配置（用于选表等简单任务）</Divider>

      <Typography.Paragraph style={{ fontSize: '12px', color: '#666', marginBottom: '16px' }}>
        快速模型用于选表、生成标题等简单任务，可以降低成本并提高响应速度。
        如果未配置，将使用主模型进行所有操作。
      </Typography.Paragraph>

      <Form layout="vertical">
        {Object.keys(FastAIFormConfig[aiConfig?.aiSqlSource || AIType.OPENAI]).map((key: string) => {
          const fieldType = getFieldType(key);
          const isRequired = key === 'apiKey' && fastAiConfig?.apiKey;
          const placeholder = FastAIFormConfig[aiConfig?.aiSqlSource || AIType.OPENAI]?.[key] as string;
          const hasPlaceholder = placeholder && placeholder !== 'true';
          
          return (
            <Form.Item
              key={key}
              required={isRequired}
              label={formatParamName(key)}
              className={styles.title}
            >
              {fieldType === 'number' ? (
                <InputNumber
                  style={{ width: '100%' }}
                  value={fastAiConfig?.[key as keyof IFastAIConfig] as number}
                  placeholder={hasPlaceholder ? placeholder : undefined}
                  onChange={(value) => {
                    setFastAiConfig({ ...fastAiConfig, [key]: value });
                  }}
                  min={key === 'temperature' || key === 'topP' ? 0 : undefined}
                  max={key === 'temperature' || key === 'topP' ? 1 : undefined}
                  step={key === 'temperature' || key === 'topP' ? 0.1 : 1}
                />
              ) : (
                <Input
                  autoComplete="off"
                  value={fastAiConfig?.[key as keyof IFastAIConfig] as string}
                  placeholder={hasPlaceholder ? placeholder : undefined}
                  onChange={(e) => {
                    setFastAiConfig({ ...fastAiConfig, [key]: e.target.value });
                  }}
                />
              )}
            </Form.Item>
          );
        })}
      </Form>

      <div className={styles.bottomButton}>
        <Button type="primary" onClick={handleApplyFastAiConfig}>
          {i18n('setting.button.apply')} - 快速模型
        </Button>
      </div>
    </>
  );
}

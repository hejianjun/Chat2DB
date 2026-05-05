import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Select, Space, Table } from 'antd';
import configService from '@/service/config';
import { AIType } from '@/typings/ai';
import { IDefaultModelConfig, IModelItem, IModelServiceConfig } from '@/typings/setting';
import i18n from '@/i18n';
import { IRole } from '@/typings/user';
import { useUserStore } from '@/store/user';
import styles from './index.less';

interface IProps {
  mode: 'service' | 'default';
}

const providerOptions = [
  { label: 'OpenAI', value: AIType.OPENAI },
  { label: 'Anthropic', value: AIType.ANTHROPIC },
];

export default function SettingAI(props: IProps) {
  const [modelServices, setModelServices] = useState<IModelServiceConfig[]>([]);
  const [defaultModelConfig, setDefaultModelConfig] = useState<IDefaultModelConfig>({ defaultModelId: '', fastModelId: '' });
  const [editingService, setEditingService] = useState<IModelServiceConfig | null>(null);
  const [serviceModalOpen, setServiceModalOpen] = useState(false);
  const [serviceForm] = Form.useForm<IModelServiceConfig>();
  const { userInfo } = useUserStore((state) => ({ userInfo: state.curUser }));

  useEffect(() => {
    loadData();
  }, []);

  const modelOptions = useMemo(() => {
    return modelServices.flatMap((service) =>
      (service.modelList || []).map((model) => ({
        label: `${model.name} (${service.name})`,
        value: model.id || `${service.id || service.name}:${model.model}`,
      })),
    );
  }, [modelServices]);

  const loadData = async () => {
    const [serviceList, modelConfig] = await Promise.all([
      configService.getModelServiceList(),
      configService.getDefaultModelConfig(),
    ]);
    setModelServices(serviceList || []);
    setDefaultModelConfig(modelConfig || { defaultModelId: '', fastModelId: '' });
  };

  if (userInfo?.roleCode && userInfo?.roleCode === IRole.USER) {
    return <Alert description={i18n('setting.ai.user.hidden')} type="warning" showIcon />;
  }

  const openCreate = () => {
    setEditingService(null);
    serviceForm.setFieldsValue({
      provider: AIType.OPENAI,
      modelList: [{ name: '', model: '' }],
    } as IModelServiceConfig);
    setServiceModalOpen(true);
  };

  const openEdit = (record: IModelServiceConfig) => {
    setEditingService(record);
    serviceForm.setFieldsValue({
      ...record,
      modelList: record.modelList?.length ? record.modelList : [{ name: '', model: '' }],
    });
    setServiceModalOpen(true);
  };

  const handleDelete = async (id?: string) => {
    if (!id) {
      return;
    }
    await configService.deleteModelService({ id });
    await loadData();
  };

  const handleSaveService = async () => {
    const values = await serviceForm.validateFields();
    await configService.upsertModelService({
      ...values,
      id: editingService?.id,
      modelList: (values.modelList || []).filter((model) => model?.name && model?.model),
    });
    setServiceModalOpen(false);
    await loadData();
  };

  const handleSaveDefaultModel = async () => {
    await configService.setDefaultModelConfig(defaultModelConfig);
    await loadData();
  };

  if (props.mode === 'default') {
    return (
      <>
        <Form layout="vertical">
          <Form.Item required label="默认模型" className={styles.title}>
            <Select
              placeholder="请选择默认模型"
              value={defaultModelConfig.defaultModelId || undefined}
              options={modelOptions}
              onChange={(value) => {
                setDefaultModelConfig({ ...defaultModelConfig, defaultModelId: value });
              }}
            />
          </Form.Item>
          <Form.Item label="快速模型" className={styles.title}>
            <Select
              allowClear
              placeholder="请选择快速模型"
              value={defaultModelConfig.fastModelId || undefined}
              options={modelOptions}
              onChange={(value) => {
                setDefaultModelConfig({ ...defaultModelConfig, fastModelId: value || '' });
              }}
            />
          </Form.Item>
        </Form>
        <div className={styles.bottomButton}>
          <Button type="primary" onClick={handleSaveDefaultModel} disabled={!defaultModelConfig.defaultModelId}>
            保存默认模型
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <div className={styles.bottomButton} style={{ justifyContent: 'flex-start', marginTop: 0, marginBottom: 12 }}>
        <Button type="primary" onClick={openCreate}>
          新增模型服务
        </Button>
      </div>

      <Table
        rowKey={(record) => record.id || record.name}
        dataSource={modelServices}
        pagination={false}
        columns={[
          { title: '服务名称', dataIndex: 'name' },
          { title: '厂商', dataIndex: 'provider' },
          {
            title: '模型',
            dataIndex: 'modelList',
            render: (models: IModelItem[]) => (models || []).map((item) => item.name).join(', '),
          },
          {
            title: '操作',
            render: (_, record: IModelServiceConfig) => (
              <Space>
                <Button type="link" onClick={() => openEdit(record)}>
                  编辑
                </Button>
                <Button type="link" danger onClick={() => handleDelete(record.id)}>
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editingService ? '编辑模型服务' : '新增模型服务'}
        open={serviceModalOpen}
        onCancel={() => setServiceModalOpen(false)}
        onOk={handleSaveService}
        destroyOnClose
      >
        <Form form={serviceForm} layout="vertical">
          <Form.Item name="name" label="服务名称" rules={[{ required: true, message: '请输入服务名称' }]}>
            <Input placeholder="例如 OpenAI-Prod" />
          </Form.Item>
          <Form.Item name="provider" label="厂商" rules={[{ required: true, message: '请选择厂商' }]}>
            <Select options={providerOptions} />
          </Form.Item>
          <Form.Item name="apiHost" label="API Host">
            <Input />
          </Form.Item>
          <Form.Item name="apiKey" label="API Key">
            <Input.Password />
          </Form.Item>
          <Form.List name="modelList">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                    <Form.Item
                      name={[field.name, 'name']}
                      rules={[{ required: true, message: '模型名称必填' }]}
                    >
                      <Input placeholder="展示名" />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'model']}
                      rules={[{ required: true, message: '模型ID必填' }]}
                    >
                      <Input placeholder="模型ID，如 gpt-4o-mini" />
                    </Form.Item>
                    <Button danger onClick={() => remove(field.name)}>
                      删除
                    </Button>
                  </Space>
                ))}
                <Button onClick={() => add({ name: '', model: '' })}>新增模型</Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </>
  );
}

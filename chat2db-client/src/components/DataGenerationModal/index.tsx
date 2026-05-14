import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Modal, Form, Button, Table, Select, InputNumber, message, Progress, Space } from 'antd';
import styles from './index.less';
import { setOpenDataGenerationModal } from '@/pages/main/workspace/store/modal';
import createRequest from '@/service/base';

const { Option } = Select;

export interface IDataGenerationModalParams {
  dataSourceId: number;
  databaseName: string;
  schemaName?: string;
  tableName: string;
}

interface GeneratorSubType {
  value: string;
  label: string;
}

interface GeneratorConfigField {
  key: string;
  label: string;
  type: string;
  defaultValue: any;
}

interface GeneratorMetadata {
  generatorType: string;
  label: string;
  subTypes: GeneratorSubType[];
  configFields: GeneratorConfigField[];
}

interface ColumnConfig {
  columnName: string;
  dataType: string;
  comment?: string;
  generationType: string;
  subType?: string;
  nullable: boolean;
  maxLength?: number;
  scale?: number;
  customParams?: Record<string, any>;
}

interface PreviewRow {
  [key: string]: any;
}

interface PreviewVO {
  tableName: string;
  previewData: PreviewRow[];
  columns: {
    columnName: string;
    dataType: string;
    generationType: string;
    comment?: string;
  }[];
}

const DataGenerationModal: React.FC = () => {
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [tableInfo, setTableInfo] = useState<IDataGenerationModalParams | null>(null);
  const [columns, setColumns] = useState<ColumnConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [generatorMetadata, setGeneratorMetadata] = useState<GeneratorMetadata[]>([]);
  const [previewData, setPreviewData] = useState<PreviewRow[]>([]);
  const [showPreview, setShowPreview] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [progress, setProgress] = useState(0);
  const tableInfoRef = useRef<IDataGenerationModalParams | null>(null);

  const openDataGenerationModal = useCallback((params: IDataGenerationModalParams) => {
    setOpen(true);
    setTableInfo(params);
    tableInfoRef.current = params;
  }, []);

  useEffect(() => {
    setOpenDataGenerationModal(openDataGenerationModal);
  }, [openDataGenerationModal]);

  useEffect(() => {
    if (open) {
      loadGeneratorMetadata();
    }
  }, [open]);

  useEffect(() => {
    if (open && tableInfo) {
      loadTableColumns();
    }
  }, [open, tableInfo]);

  const loadGeneratorMetadata = async () => {
    try {
      const request = createRequest<{ data: GeneratorMetadata[] }>(
        '/api/rdb/table/generate-data/metadata',
        'get'
      );
      const res = await request();
      if (res?.data) {
        setGeneratorMetadata(res.data);
      }
    } catch (error) {
      console.error('Failed to load generator metadata', error);
    }
  };

  const loadTableColumns = async () => {
    if (!tableInfo) return;
    setLoading(true);
    try {
      const request = createRequest<{ data: ColumnConfig[] }>(
        '/api/rdb/table/generate-data/config',
        'post'
      );
      const res = await request({
        dataSourceId: tableInfo.dataSourceId,
        databaseName: tableInfo.databaseName,
        schemaName: tableInfo.schemaName,
        tableName: tableInfo.tableName,
      });
      if (res?.data) {
        setColumns(res.data);
      }
    } catch (error) {
      message.error('加载表列信息失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAiGuess = async () => {
    message.info('AI推断功能开发中');
  };

  const buildColumnConfigs = () => {
    return columns.map(col => ({
      columnName: col.columnName,
      dataType: col.dataType,
      generationType: col.generationType,
      subType: col.subType,
      nullable: col.nullable,
      maxLength: col.maxLength,
      scale: col.scale,
      customParams: col.customParams,
    }));
  };

  const handlePreview = async () => {
    if (!tableInfo) return;
    setLoading(true);
    try {
      const rowCount = form.getFieldValue('rowCount') || 10;
      const request = createRequest<{ data: PreviewVO }>(
        '/api/rdb/table/generate-data/preview',
        'post'
      );
      const res = await request({
        dataSourceId: tableInfo.dataSourceId,
        databaseName: tableInfo.databaseName,
        schemaName: tableInfo.schemaName,
        tableName: tableInfo.tableName,
        rowCount,
        columnConfigs: buildColumnConfigs(),
      });
      if (res?.data) {
        setPreviewData(res.data.previewData || []);
        setShowPreview(true);
      }
    } catch (error) {
      message.error('生成预览失败');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    if (!tableInfo) return;
    setGenerating(true);
    setProgress(0);
    try {
      const rowCount = form.getFieldValue('rowCount') || 100;
      const request = createRequest<{ data: number }>(
        '/api/rdb/table/generate-data/execute',
        'post'
      );
      const res = await request({
        dataSourceId: tableInfo.dataSourceId,
        databaseName: tableInfo.databaseName,
        schemaName: tableInfo.schemaName,
        tableName: tableInfo.tableName,
        rowCount,
        columnConfigs: buildColumnConfigs(),
      });
      if (res?.data) {
        message.success('数据生成任务已创建， taskId: ' + res.data);
        setOpen(false);
      }
    } catch (error) {
      message.error('数据生成失败');
    } finally {
      setGenerating(false);
      setProgress(0);
    }
  };

  const handleColumnTypeChange = (columnName: string, generationType: string) => {
    setColumns(prev => prev.map(col => {
      if (col.columnName === columnName) {
        const meta = generatorMetadata.find(m => m.generatorType === generationType);
        const defaultSubType = meta?.subTypes?.[0]?.value;
        return { ...col, generationType, subType: defaultSubType };
      }
      return col;
    }));
  };

  const handleSubTypeChange = (columnName: string, subType: string) => {
    setColumns(prev => prev.map(col =>
      col.columnName === columnName ? { ...col, subType } : col
    ));
  };

  const handleCustomParamChange = (columnName: string, fieldKey: string, value: any) => {
    setColumns(prev => prev.map(col => {
      if (col.columnName === columnName) {
        const customParams = { ...(col.customParams || {}), [fieldKey]: value };
        return { ...col, customParams };
      }
      return col;
    }));
  };

  const getGeneratorMeta = (generationType: string): GeneratorMetadata | undefined => {
    return generatorMetadata.find(m => m.generatorType === generationType);
  };

  const columnsConfig = [
    {
      title: '列名',
      dataIndex: 'columnName',
      key: 'columnName',
      width: 140,
    },
    {
      title: '数据类型',
      dataIndex: 'dataType',
      key: 'dataType',
      width: 100,
    },
    {
      title: '注释',
      dataIndex: 'comment',
      key: 'comment',
      width: 100,
    },
    {
      title: '生成类型',
      key: 'generationType',
      width: 130,
      render: (_: any, record: ColumnConfig) => (
        <Select
          value={record.generationType}
          onChange={(value) => handleColumnTypeChange(record.columnName, value)}
          style={{ width: '100%' }}
          size="small"
        >
          {generatorMetadata.map(meta => (
            <Option key={meta.generatorType} value={meta.generatorType}>
              {meta.label}
            </Option>
          ))}
        </Select>
      ),
    },
    {
      title: '子类型',
      key: 'subType',
      width: 130,
      render: (_: any, record: ColumnConfig) => {
        const meta = getGeneratorMeta(record.generationType);
        if (!meta?.subTypes?.length) return null;
        return (
          <Select
            value={record.subType}
            onChange={(value) => handleSubTypeChange(record.columnName, value)}
            style={{ width: '100%' }}
            size="small"
            placeholder="选择子类型"
          >
            {meta.subTypes.map(st => (
              <Option key={st.value} value={st.value}>
                {st.label}
              </Option>
            ))}
          </Select>
        );
      },
    },
    {
      title: '自定义参数',
      key: 'customParams',
      width: 200,
      render: (_: any, record: ColumnConfig) => {
        const meta = getGeneratorMeta(record.generationType);
        if (!meta?.configFields?.length) return null;
        return (
          <Space size={4} wrap>
            {meta.configFields.map(field => (
              <InputNumber
                key={field.key}
                size="small"
                style={{ width: 80 }}
                placeholder={field.label}
                value={(record.customParams as any)?.[field.key] ?? field.defaultValue}
                onChange={(value) => handleCustomParamChange(record.columnName, field.key, value)}
              />
            ))}
          </Space>
        );
      },
    },
  ];

  const previewColumns = previewData.length > 0
    ? Object.keys(previewData[0]).map(key => ({
        title: key,
        dataIndex: key,
        key,
        width: 120,
        ellipsis: true,
      }))
    : [];

  return (
    <Modal
      title="生成数据"
      open={open}
      onCancel={() => setOpen(false)}
      width={1100}
      footer={[
        <Button key="cancel" onClick={() => setOpen(false)}>
          取消
        </Button>,
        <Button key="preview" onClick={handlePreview} loading={loading}>
          预览
        </Button>,
        <Button key="generate" type="primary" onClick={handleGenerate} loading={generating}>
          确定生成
        </Button>,
      ]}
    >
      <Form form={form} layout="vertical">
        <Form.Item label="生成行数" name="rowCount" initialValue={100}>
          <InputNumber min={1} max={100000} style={{ width: 200 }} />
        </Form.Item>

        <Space style={{ marginBottom: 16 }}>
          <Button onClick={handleAiGuess} loading={loading}>
            AI猜一猜
          </Button>
        </Space>

        <Table
          columns={columnsConfig}
          dataSource={columns}
          rowKey="columnName"
          pagination={false}
          size="small"
          loading={loading}
          scroll={{ y: 250 }}
        />

        {showPreview && (
          <div style={{ marginTop: 16 }}>
            <h4>预览数据（前10行）</h4>
            <Table
              columns={previewColumns}
              dataSource={previewData}
              rowKey={(record, index) => String(index)}
              pagination={false}
              size="small"
              scroll={{ x: true, y: 150 }}
            />
          </div>
        )}

        {generating && (
          <div style={{ marginTop: 16 }}>
            <h4>生成进度</h4>
            <Progress percent={progress} status={progress === 100 ? 'success' : 'active'} />
          </div>
        )}
      </Form>
    </Modal>
  );
};

export default DataGenerationModal;

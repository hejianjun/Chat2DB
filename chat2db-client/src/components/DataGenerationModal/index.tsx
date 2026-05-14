import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Modal, Form, Button, Table, Select, InputNumber, message, Space } from 'antd';
import { setOpenDataGenerationModal } from '@/pages/main/workspace/store/modal';
import createRequest from '@/service/base';

const { Option } = Select;

export interface IDataGenerationModalParams {
  dataSourceId: number;
  databaseName: string;
  schemaName?: string;
  tableName: string;
}

interface TableInfo {
  dataSourceId: number;
  databaseName: string;
  schemaName?: string;
  tableName: string;
}

interface ColumnConfigVO {
  columnName: string;
  dataType: string;
  generationType: string;
  subType: string;
  comment?: string;
  nullable: boolean;
  maxLength?: number;
  scale?: number;
  customParams?: Record<string, any>;
}

interface GenerateRequest {
  dataSourceId: number;
  databaseName: string;
  schemaName?: string;
  tableName: string;
  rowCount?: number;
  columnConfigs?: ColumnConfigVO[];
  batchSize?: number;
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

interface SavedRule {
  columnName: string;
  generationType: string;
  subType: string;
  customParams: string;
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

const loadGeneratorMetadata = createRequest<void, GeneratorMetadata[]>('/api/rdb/table/generate-data/metadata', { method: 'get' });

const loadTableColumns = createRequest<TableInfo, ColumnConfig[]>('/api/rdb/table/generate-data/config', { method: 'post' });

const loadSavedRules = createRequest<TableInfo, SavedRule[]>('/api/rdb/table/generate-data/generation-rule/list', { method: 'get' });

const generatePreview = createRequest<GenerateRequest, PreviewVO>('/api/rdb/table/generate-data/preview', { method: 'post' });

const executeGeneration = createRequest<GenerateRequest, number>('/api/rdb/table/generate-data/execute', { method: 'post' });

const DataGenerationModal: React.FC = () => {
  const [form] = Form.useForm();
  const [open, setOpen] = useState(false);
  const [tableInfo, setTableInfo] = useState<IDataGenerationModalParams | null>(null);
  const [columns, setColumns] = useState<ColumnConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [generatorMetadata, setGeneratorMetadata] = useState<GeneratorMetadata[]>([]);
  const [previewData, setPreviewData] = useState<PreviewRow[]>([]);
  const [showPreview, setShowPreview] = useState(false);
  const tableInfoRef = useRef<IDataGenerationModalParams | null>(null);

  const openDataGenerationModal = useCallback((params: IDataGenerationModalParams) => {
    setOpen(true);
    setTableInfo(params);
    tableInfoRef.current = params;
    setPreviewData([]);
    setShowPreview(false);
  }, []);

  useEffect(() => {
    setOpenDataGenerationModal(openDataGenerationModal);
  }, [openDataGenerationModal]);

  useEffect(() => {
    if (open && generatorMetadata.length === 0) {
      loadGeneratorMetadata({})
        .then((res) => {
          if (res) setGeneratorMetadata(res);
        })
        .catch(console.error);
    }
  }, [open]);

  useEffect(() => {
    if (open && tableInfo) {
      fetchTableColumns();
    }
  }, [open, tableInfo]);

  const fetchTableColumns = async () => {
    if (!tableInfo) return;
    setLoading(true);
    try {
      const columnsRes = await loadTableColumns({
        dataSourceId: tableInfo.dataSourceId,
        databaseName: tableInfo.databaseName,
        schemaName: tableInfo.schemaName,
        tableName: tableInfo.tableName,
      });
      if (columnsRes) {
        setColumns(columnsRes);
      }
    } catch {
      message.error('加载表列信息失败');
    } finally {
      setLoading(false);
    }
  };

  const buildColumnConfigs = (): ColumnConfigVO[] => {
    return columns.map(col => ({
      columnName: col.columnName,
      dataType: col.dataType,
      generationType: col.generationType,
      subType: col.subType || '',
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
      const res = await generatePreview({
        dataSourceId: tableInfo.dataSourceId,
        databaseName: tableInfo.databaseName,
        schemaName: tableInfo.schemaName,
        tableName: tableInfo.tableName,
        rowCount,
        columnConfigs: buildColumnConfigs(),
      });
      if (res) {
        setPreviewData(res.previewData || []);
        setShowPreview(true);
      }
    } catch {
      message.error('生成预览失败');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    if (!tableInfo) return;
    try {
      const rowCount = form.getFieldValue('rowCount') || 100;
      const res = await executeGeneration({
        dataSourceId: tableInfo.dataSourceId,
        databaseName: tableInfo.databaseName,
        schemaName: tableInfo.schemaName,
        tableName: tableInfo.tableName,
        rowCount,
        columnConfigs: buildColumnConfigs(),
      });
      if (res) {
        message.success('数据生成任务已创建，任务ID: ' + res);
        setOpen(false);
      }
    } catch {
      message.error('数据生成失败');
    }
  };

  const handleColumnTypeChange = (columnName: string, generationType: string) => {
    setColumns(prev => prev.map(col => {
      if (col.columnName === columnName) {
        const meta = generatorMetadata.find(m => m.generatorType === generationType);
        return { ...col, generationType, subType: meta?.subTypes?.[0]?.value };
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

  const getGeneratorMeta = (generationType: string) => {
    return generatorMetadata.find(m => m.generatorType === generationType);
  };

  const tableColumns = [
    { title: '列名', dataIndex: 'columnName', key: 'columnName', width: 140 },
    { title: '数据类型', dataIndex: 'dataType', key: 'dataType', width: 100 },
    { title: '注释', dataIndex: 'comment', key: 'comment', width: 100 },
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
            <Option key={meta.generatorType} value={meta.generatorType}>{meta.label}</Option>
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
              <Option key={st.value} value={st.value}>{st.label}</Option>
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
    ? Object.keys(previewData[0]).map(key => ({ title: key, dataIndex: key, key, width: 120, ellipsis: true }))
    : [];

  return (
    <Modal
      title="生成数据"
      open={open}
      onCancel={() => setOpen(false)}
      width={1100}
      footer={[
        <Button key="cancel" onClick={() => setOpen(false)}>取消</Button>,
        <Button key="preview" onClick={handlePreview} loading={loading}>预览</Button>,
        <Button key="generate" type="primary" onClick={handleGenerate} loading={loading}>确定生成</Button>,
      ]}
    >
      <Form form={form} layout="vertical">
        <Form.Item label="生成行数" name="rowCount" initialValue={100}>
          <InputNumber min={1} max={100000} style={{ width: 200 }} />
        </Form.Item>

        <Table
          columns={tableColumns}
          dataSource={columns}
          rowKey="columnName"
          pagination={false}
          size="small"
          loading={loading}
          scroll={{ y: 300 }}
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
      </Form>
    </Modal>
  );
};

export default DataGenerationModal;

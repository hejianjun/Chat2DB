import React, { useState, useEffect } from 'react';
import { Modal, Form, Input, Button, Table, Select, InputNumber, message, Progress, Space } from 'antd';
import styles from './index.less';

const { Option } = Select;

interface DataGenerationModalProps {
  visible: boolean;
  onCancel: () => void;
  onOk: (config: DataGenerationConfig) => void;
  tableInfo: {
    dataSourceId: number;
    databaseName: string;
    schemaName?: string;
    tableName: string;
  };
}

interface ColumnConfig {
  columnName: string;
  dataType: string;
  comment?: string;
  generationType: string;
  nullable: boolean;
}

interface DataGenerationConfig {
  rowCount: number;
  columnConfigs: Record<string, string>;
}

const DataGenerationModal: React.FC<DataGenerationModalProps> = ({
  visible,
  onCancel,
  onOk,
  tableInfo
}) => {
    const [form] = Form.useForm();
  const [columns, setColumns] = useState<ColumnConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewData, setPreviewData] = useState<any[]>([]);
  const [showPreview, setShowPreview] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [progress, setProgress] = useState(0);

  // 支持的数据生成类型
  const generationTypes = [
    { value: 'text', label: '文本' },
    { value: 'name', label: '姓名' },
    { value: 'email', label: '邮箱' },
    { value: 'phone', label: '电话' },
    { value: 'address', label: '地址' },
    { value: 'company', label: '公司' },
    { value: 'date', label: '日期' },
    { value: 'datetime', label: '日期时间' },
    { value: 'number', label: '数字' },
    { value: 'numeric', label: '数值' },
  ];

  useEffect(() => {
    if (visible) {
      loadTableColumns();
    }
  }, [visible]);

  const loadTableColumns = async () => {
    setLoading(true);
    try {
      // TODO: 调用API获取表列信息
      // const response = await dataGenerationService.getTableColumns(tableInfo);
      // setColumns(response.data);
      
      // 模拟数据
      const mockColumns: ColumnConfig[] = [
        { columnName: 'id', dataType: 'BIGINT', comment: '主键ID', generationType: 'number', nullable: false },
        { columnName: 'name', dataType: 'VARCHAR', comment: '姓名', generationType: 'name', nullable: false },
        { columnName: 'email', dataType: 'VARCHAR', comment: '邮箱地址', generationType: 'email', nullable: true },
        { columnName: 'phone', dataType: 'VARCHAR', comment: '电话号码', generationType: 'phone', nullable: true },
        { columnName: 'address', dataType: 'TEXT', comment: '地址', generationType: 'address', nullable: true },
        { columnName: 'created_at', dataType: 'TIMESTAMP', comment: '创建时间', generationType: 'datetime', nullable: false },
      ];
      setColumns(mockColumns);
    } catch (error) {
      message.error('加载表列信息失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAiGuess = async () => {
    setLoading(true);
    try {
      // TODO: 调用AI推断API
      // const response = await dataGenerationService.aiInferGenerationTypes(tableInfo);
      // 更新columns的generationType
      
      message.success('AI推断完成');
    } catch (error) {
      message.error('AI推断失败');
    } finally {
      setLoading(false);
    }
  };

  const handlePreview = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      // TODO: 调用预览API
      // const response = await dataGenerationService.generatePreview({
      //   ...tableInfo,
      //   rowCount: 10,
      //   columnConfigs: values.columnConfigs
      // });
      // setPreviewData(response.data.previewData);
      // setShowPreview(true);
      
      // 模拟预览数据
      const mockPreview = [
        { id: 1, name: '张三', email: 'zhangsan@example.com', phone: '13800138000', address: '北京市朝阳区', created_at: '2023-01-01 10:00:00' },
        { id: 2, name: '李四', email: 'lisi@example.com', phone: '13800138001', address: '上海市浦东新区', created_at: '2023-01-02 10:00:00' },
      ];
      setPreviewData(mockPreview);
      setShowPreview(true);
    } catch (error) {
      message.error('生成预览失败');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    const values = await form.validateFields();
    setGenerating(true);
    setProgress(0);
    
    try {
      // TODO: 调用数据生成API
      // const response = await dataGenerationService.executeDataGeneration({
      //   ...tableInfo,
      //   ...values
      // });
      
      // 模拟进度更新
      const progressInterval = setInterval(() => {
        setProgress(prev => {
          if (prev >= 100) {
            clearInterval(progressInterval);
            return 100;
          }
          return prev + 10;
        });
      }, 500);

      setTimeout(() => {
        clearInterval(progressInterval);
        setProgress(100);
        setGenerating(false);
        message.success('数据生成完成');
        onOk(values);
      }, 5000);
      
    } catch (error) {
      message.error('数据生成失败');
      setGenerating(false);
    }
  };

  const handleColumnTypeChange = (columnName: string, generationType: string) => {
    setColumns(prev => prev.map(col => 
      col.columnName === columnName 
        ? { ...col, generationType }
        : col
    ));
  };

  const columnsConfig = [
    {
      title: '列名',
      dataIndex: 'columnName',
      key: 'columnName',
      width: 150,
    },
    {
      title: '数据类型',
      dataIndex: 'dataType',
      key: 'dataType',
      width: 120,
    },
    {
      title: '注释',
      dataIndex: 'comment',
      key: 'comment',
      width: 150,
    },
    {
      title: '生成类型',
      key: 'generationType',
      width: 150,
      render: (text: string, record: ColumnConfig) => (
        <Select
          value={record.generationType}
          onChange={(value) => handleColumnTypeChange(record.columnName, value)}
          style={{ width: '100%' }}
          size="small"
        >
          {generationTypes.map(type => (
            <Option key={type.value} value={type.value}>
              {type.label}
            </Option>
          ))}
        </Select>
      ),
    },
  ];

  const previewColumns = columns.map(col => ({
    title: col.columnName,
    dataIndex: col.columnName,
    key: col.columnName,
    width: 120,
  }));

  return (
    <Modal
      title="生成数据"
      open={visible}
      onCancel={onCancel}
      width={900}
      footer={[
        <Button key="cancel" onClick={onCancel}>
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
          <InputNumber min={1} max={100000} style={{ width: '100%' }} />
        </Form.Item>
        
        <Space style={{ marginBottom: 16 }}>
          <Button onClick={handleAiGuess} loading={loading}>
            猜一猜
          </Button>
        </Space>

        <Table
          columns={columnsConfig}
          dataSource={columns}
          rowKey="columnName"
          pagination={false}
          size="small"
          loading={loading}
          scroll={{ y: 200 }}
        />

        {showPreview && (
          <div style={{ marginTop: 16 }}>
            <h4>预览数据（前10行）</h4>
            <Table
              columns={previewColumns}
              dataSource={previewData}
              rowKey="id"
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

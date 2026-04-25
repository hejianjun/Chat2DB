import React, { useState, useRef, useEffect } from 'react';
import { Modal, Upload, Select, message, Button, Progress } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import i18n from '@/i18n';
import taskService from '@/service/task';
import { setOpenImportDataModal } from '@/pages/main/workspace/store/modal';

const { Dragger } = Upload;

export interface IImportDataModalParams {
  tableName: string;
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string;
  executedCallback?: () => void;
}

const ImportDataModal = () => {
  const [open, setOpen] = useState(false);
  const [params, setParams] = useState<IImportDataModalParams | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileType, setFileType] = useState<string>('CSV');
  const [importProgress, setImportProgress] = useState<number>(0);
  const [importModalVisible, setImportModalVisible] = useState<boolean>(false);
  const [importing, setImporting] = useState<boolean>(false);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const executedCallbackRef = useRef<IImportDataModalParams['executedCallback']>();

  useEffect(() => {
    if (!open) {
      setFile(null);
      setImportProgress(0);
      setLogs([]);
    }
  }, [open]);

  const openImportDataModal = (importParams: IImportDataModalParams) => {
    setOpen(true);
    setParams(importParams);
    executedCallbackRef.current = importParams.executedCallback;
  };

  useEffect(() => {
    setOpenImportDataModal(openImportDataModal);
  }, []);

  const handleFileChange = (info: any) => {
    if (info.file) {
      setFile(info.file.originFileObj || info.file);
    }
  };

  const addLog = (log: string) => {
    const timestamp = new Date().toLocaleString();
    setLogs(prev => [...prev, `${timestamp}: ${log}`]);
  };

  const handleImport = async () => {
    if (!file || !params) {
      message.error(i18n('workspace.table.import.selectFile'));
      return;
    }

    setImporting(true);
    setImportModalVisible(true);
    setImportProgress(0);
    setLogs([]);
    addLog('start------');

    try {
      const taskId = await taskService.importData({
        file,
        tableName: params.tableName,
        fileType,
        dataSourceId: params.dataSourceId,
        databaseName: params.databaseName,
        schemaName: params.schemaName,
      } as any);

      addLog(`Task created: ${taskId}`);
      startImportPolling(taskId);
    } catch (error) {
      addLog(`Error: ${error}`);
      message.error(i18n('common.text.importFailed'));
      setImporting(false);
      setImportModalVisible(false);
    }
  };

  const startImportPolling = (taskId: number) => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
    }

    pollingRef.current = setInterval(async () => {
      try {
        const task = await taskService.getTask({ id: taskId });
        if (task) {
          const processedCount = parseInt(task.taskProgress || '0', 10);
          setImportProgress(processedCount);
          addLog(`Progress: ${processedCount} rows imported`);

          if (task.taskStatus === 'FINISH') {
            clearInterval(pollingRef.current!);
            pollingRef.current = null;
            addLog('Import completed successfully');
            message.success(i18n('common.text.importSuccess'));
            setImporting(false);
            executedCallbackRef.current?.();
            setTimeout(() => {
              setImportModalVisible(false);
              setOpen(false);
            }, 2000);
          } else if (task.taskStatus === 'ERROR') {
            clearInterval(pollingRef.current!);
            pollingRef.current = null;
            addLog('Import failed');
            message.error(i18n('common.text.importFailed'));
            setImporting(false);
          }
        }
      } catch (error) {
        clearInterval(pollingRef.current!);
        pollingRef.current = null;
        addLog(`Polling error: ${error}`);
        message.error(i18n('common.text.importFailed'));
        setImporting(false);
      }
    }, 1000);
  };

  const handleClose = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    setImportModalVisible(false);
    if (!importing) {
      setOpen(false);
    }
  };

  const uploadProps = {
    name: 'file',
    multiple: false,
    showUploadList: false,
    onChange: handleFileChange,
    beforeUpload: (uploadFile: File) => {
      setFile(uploadFile);
      return false;
    },
  };

  const fileTypes = [
    { label: i18n('workspace.table.import.fileType.csv'), value: 'CSV' },
    { label: i18n('workspace.table.import.fileType.xlsx'), value: 'XLSX' },
    { label: i18n('workspace.table.import.fileType.xls'), value: 'XLS' },
    { label: i18n('workspace.table.import.fileType.sql'), value: 'SQL' },
  ];

  return !!params && (
    <>
      <Modal
        title={i18n('workspace.table.import.title')}
        open={open}
        onCancel={() => setOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setOpen(false)} disabled={importing}>
            {i18n('workspace.table.import.cancel')}
          </Button>,
          <Button key="start" type="primary" onClick={handleImport} disabled={!file || importing}>
            {i18n('workspace.table.import.start')}
          </Button>,
        ]}
        width={500}
      >
        <div style={{ padding: '16px 0' }}>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 8, color: 'var(--color-text)' }}>
              {i18n('workspace.table.import.targetTable')}:
            </label>
            <div style={{ padding: '8px 12px', background: 'var(--color-bg-layout)', borderRadius: 4, color: 'var(--color-text)' }}>
              {params?.tableName}
            </div>
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 8, color: 'var(--color-text)' }}>
              {i18n('workspace.table.import.fileType')}:
            </label>
            <Select
              value={fileType}
              onChange={setFileType}
              options={fileTypes}
              style={{ width: '200px' }}
              disabled={importing}
            />
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 8, color: 'var(--color-text)' }}>
              {i18n('workspace.table.import.uploadFile')}:
            </label>
            <Dragger {...uploadProps}>
              <p className="ant-upload-drag-icon">
                <UploadOutlined />
              </p>
              <p className="ant-upload-text">{i18n('workspace.table.import.uploadFile')}</p>
              <p className="ant-upload-hint">
                {file ? file.name : i18n('workspace.table.import.uploadHint')}
              </p>
            </Dragger>
          </div>
        </div>
      </Modal>

      <Modal
        title={i18n('workspace.table.import.progress.log')}
        open={importModalVisible}
        footer={[
          <Button key="close" onClick={handleClose}>
            {i18n('workspace.table.import.close')}
          </Button>,
        ]}
        width={600}
        closable={false}
      >
        <div style={{ color: 'var(--color-text)' }}>
          <div style={{ marginBottom: 16 }}>
            <div>{i18n('workspace.table.import.progress.taskName')}: Import {params?.tableName}</div>
          </div>
          <div style={{ 
            maxHeight: '300px', 
            overflowY: 'auto', 
            background: 'var(--color-bg-layout)', 
            padding: '12px', 
            borderRadius: '4px',
            marginBottom: '16px',
            fontFamily: 'monospace',
            fontSize: '12px'
          }}>
            {logs.map((log, index) => (
              <div key={index} style={{ marginBottom: '4px' }}>{log}</div>
            ))}
          </div>
          <Progress percent={importProgress > 0 ? Math.min(importProgress, 100) : 0} status="active" />
          <div style={{ marginTop: 8 }}>
            {i18n('workspace.table.import.progress.rows')}: {importProgress}
          </div>
        </div>
      </Modal>
    </>
  );
};

export default ImportDataModal;

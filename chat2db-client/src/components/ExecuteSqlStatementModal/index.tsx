import { UploadOutlined } from '@ant-design/icons';
import { Button, Checkbox, InputNumber, Modal, Progress, Upload, message } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import i18n from '@/i18n';
import { setOpenExecuteSqlStatementModal } from '@/pages/main/workspace/store/modal';
import taskService from '@/service/task';

const { Dragger } = Upload;

export interface IExecuteSqlStatementModalParams {
  dataSourceId: number;
  databaseName?: string;
  schemaName?: string;
  executedCallback?: () => void;
}

const ExecuteSqlStatementModal = () => {
  const [open, setOpen] = useState(false);
  const [params, setParams] = useState<IExecuteSqlStatementModalParams | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileContent, setFileContent] = useState<string>('');
  const [progress, setProgress] = useState<number>(0);
  const [processedCount, setProcessedCount] = useState<number>(0);
  const [totalStatements, setTotalStatements] = useState<number>(0);
  const [importing, setImporting] = useState<boolean>(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [skipErrors, setSkipErrors] = useState<boolean>(false);
  const [commitBatchSize, setCommitBatchSize] = useState<number>(1000);
  const executedCallbackRef = useRef<IExecuteSqlStatementModalParams['executedCallback']>();
  const taskSubscriptionRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    if (!open) {
      setFile(null);
      setProgress(0);
      setProcessedCount(0);
      setTotalStatements(0);
      setFileContent('');
      setLogs([]);
      setImporting(false);
      setSkipErrors(false);
      setCommitBatchSize(1000);
    }
  }, [open]);

  useEffect(() => {
    setOpenExecuteSqlStatementModal((modalParams: IExecuteSqlStatementModalParams) => {
      setParams(modalParams);
      executedCallbackRef.current = modalParams.executedCallback;
      setOpen(true);
    });
  }, []);

  const addLog = (log: string) => {
    const timestamp = new Date().toLocaleString();
    setLogs((prev) => [...prev, `${timestamp}: ${log}`]);
  };

  const handleStart = async () => {
    if (!file || !params) {
      message.error(i18n('workspace.table.import.selectFile'));
      return;
    }
    setImporting(true);
    setLogs([]);
    setProgress(0);
    setProcessedCount(0);
    const totalCount = estimateStatementCount(fileContent);
    setTotalStatements(totalCount);
    addLog('start------');

    try {
      const taskId = await taskService.executeSqlFile({
        file,
        dataSourceId: params.dataSourceId,
        databaseName: params.databaseName,
        schemaName: params.schemaName,
        skipErrors,
        commitBatchSize,
      });
      addLog(`Task created: ${taskId}`);
      startPolling(taskId, totalCount);
    } catch (error: any) {
      addLog(`Error: ${error.message}`);
      setImporting(false);
      message.error(error.message || i18n('common.text.importFailed'));
    }
  };

  const startPolling = (taskId: number, totalCount: number) => {
    taskSubscriptionRef.current?.();
    taskSubscriptionRef.current = taskService.subscribeTask({
      id: taskId,
      onMessage: (task) => {
        const latestProcessedCount = task.taskProgress || 0;
        setProcessedCount(latestProcessedCount);
        setProgress((prev) => {
          if (totalCount > 0) {
            return Math.min(Math.round((latestProcessedCount / totalCount) * 100), 99);
          }
          return prev;
        });
        addLog(
          totalCount > 0
            ? `Progress: ${latestProcessedCount}/${totalCount} statements executed`
            : `Progress: ${latestProcessedCount} statements executed`,
        );
        if (task.taskStatus === 'FINISH') {
          taskSubscriptionRef.current = null;
          setImporting(false);
          setProgress(100);
          if (task.content) {
            addLog(task.content);
            message.warning(i18n('workspace.executeSql.completedWithSkippedErrors'));
          } else {
            addLog('Execute completed successfully');
            message.success(i18n('common.text.importSuccess'));
          }
          executedCallbackRef.current?.();
        } else if (task.taskStatus === 'ERROR') {
          taskSubscriptionRef.current = null;
          setImporting(false);
          const errorMsg = task.content || i18n('common.text.importFailed');
          addLog(`Error: ${errorMsg}`);
          message.error(errorMsg);
        }
      },
      onError: (error: any) => {
        taskSubscriptionRef.current = null;
        setImporting(false);
        addLog(`Task subscription error: ${error.message || error}`);
        message.error(i18n('common.text.importFailed'));
      },
    });
  };

  const handleClose = () => {
    taskSubscriptionRef.current?.();
    taskSubscriptionRef.current = null;
    if (!importing) {
      setOpen(false);
    }
  };

  const estimateStatementCount = (content: string) => {
    if (!content) {
      return 0;
    }
    let count = 0;
    const lines = content.split('\n');
    let current = '';
    for (const line of lines) {
      current += `${line}\n`;
      if (line.trim().endsWith(';')) {
        const sql = current.trim();
        if (sql && !sql.startsWith('--')) {
          count += 1;
        }
        current = '';
      }
    }
    const remaining = current.trim();
    if (remaining && !remaining.startsWith('--')) {
      count += 1;
    }
    return count;
  };

  const readSelectedFileContent = async (uploadFile: File) => {
    setFileContent(await uploadFile.text());
  };

  return (
    !!params && (
      <Modal
        title={i18n('workspace.menu.executeSqlStatement')}
        open={open}
        onCancel={handleClose}
        width={560}
        closable={!importing}
        footer={[
          <Button key="cancel" onClick={handleClose} disabled={importing}>
            {i18n('workspace.table.import.cancel')}
          </Button>,
          <Button key="start" type="primary" onClick={handleStart} disabled={!file || importing}>
            {i18n('workspace.table.import.start')}
          </Button>,
        ]}
      >
        <div style={{ marginBottom: 16 }}>
          <Dragger
            name="file"
            multiple={false}
            showUploadList={false}
            beforeUpload={(uploadFile: File) => {
              setFile(uploadFile);
              readSelectedFileContent(uploadFile).catch(() => undefined);
              return false;
            }}
          >
            <p className="ant-upload-drag-icon">
              <UploadOutlined />
            </p>
            <p className="ant-upload-text">{i18n('workspace.table.import.uploadFile')}</p>
            <p className="ant-upload-hint">{file ? file.name : i18n('workspace.table.import.uploadHint')}</p>
          </Dragger>
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 20,
            marginBottom: 16,
          }}
        >
          <Checkbox checked={skipErrors} disabled={importing} onChange={(event) => setSkipErrors(event.target.checked)}>
            {i18n('workspace.executeSql.skipErrors')}
          </Checkbox>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>{i18n('workspace.executeSql.commitBatchSize')}</span>
            <InputNumber
              min={0}
              max={100000}
              precision={0}
              value={commitBatchSize}
              disabled={importing}
              onChange={(value) => setCommitBatchSize(value ?? 0)}
              style={{ width: 110 }}
            />
            <span>{i18n('workspace.executeSql.statements')}</span>
          </div>
          <div style={{ width: '100%', color: 'var(--color-text-secondary)', fontSize: 12, marginTop: -12 }}>
            {i18n('workspace.executeSql.commitBatchHint')}
          </div>
        </div>
        <div
          style={{
            maxHeight: 220,
            overflowY: 'auto',
            background: 'var(--color-bg-layout)',
            padding: '12px',
            borderRadius: '4px',
            marginBottom: '16px',
            fontFamily: 'monospace',
            fontSize: '12px',
          }}
        >
          {logs.map((log, index) => (
            <div key={index} style={{ marginBottom: '4px', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {log}
            </div>
          ))}
        </div>
        <Progress
          percent={Math.min(progress, 100)}
          status={importing ? 'active' : 'normal'}
          showInfo={totalStatements > 0 || !importing}
        />
        <div style={{ marginTop: 6, color: 'var(--color-text-secondary)', fontSize: 12 }}>
          {totalStatements > 0 ? `${processedCount}/${totalStatements} statements` : `${processedCount} statements`}
        </div>
      </Modal>
    )
  );
};

export default ExecuteSqlStatementModal;

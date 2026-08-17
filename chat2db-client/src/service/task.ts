import { IPageParams, IPageResponse } from '@/typings/common';

import createRequest from './base';

export interface ITask {
  id: number;
  gmtCreate: Date;
  gmtModified: Date;
  dataSourceId: number;
  databaseName: string;
  schemaName: string;
  tableName: string;
  deleted: string;
  userId: number;
  taskType: string;
  taskStatus: string;
  taskProgress: number;
  totalCount: number;
  taskName: string;
  downloadUrl: string;
  content?: string;
}

interface ISubscribeTaskParams {
  id: number;
  onMessage: (task: ITask) => void;
  onError?: (error: any) => void;
  fallbackInterval?: number;
}

export interface IExportResultDataParams {
  sql: string;
  originalSql: string;
  exportFileName?: string;
  exportType: string;
  exportSize: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
}

export interface IImportDataParams {
  file: File;
  tableName: string;
  fileType: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  fieldMappings?: string;
  importMode?: string;
}

export interface IPreviewHeadersParams {
  file: File;
  tableName: string;
  fileType: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
}

export interface IExecuteSqlFileParams {
  file: File;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  skipErrors?: boolean;
  commitBatchSize?: number;
}

export interface ITableColumnInfo {
  name: string;
  type: string;
  primaryKey: boolean;
}

export interface IAutoMapping {
  sourceField: string;
  targetField: string;
  matched: boolean;
}

export interface IPreviewHeadersResult {
  fileHeaders: string[];
  tableColumns: ITableColumnInfo[];
  autoMappings: IAutoMapping[];
}

export interface IExportSchemaDocParams {
  exportType: string;
  exportSize: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  refresh?: boolean;
}

export interface IDataTransferParams {
  sourceDataSourceId: number;
  sourceDatabaseName?: string;
  sourceSchemaName?: string;
  targetDataSourceId: number;
  targetDatabaseName?: string;
  targetSchemaName?: string;
  tableNames: string[];
}

const exportResultData = createRequest<IExportResultDataParams, number>('/api/export/export_data', { method: 'post' });
const exportSchemaDoc = createRequest<IExportSchemaDocParams, number>('/api/export/export_doc', { method: 'post' });
const transferData = createRequest<IDataTransferParams, number>('/api/transfer/data', { method: 'post' });
const getTask = createRequest<{ id: number }, ITask>('/api/task/get/:id', { method: 'get' });
const getTaskList = createRequest<IPageParams, IPageResponse<ITask>>('/api/task/list', { method: 'get' });
const cleanupTasks = createRequest<Record<string, never>, number>('/api/task/cleanup', { method: 'post' });

const previewFileHeaders = (params: IPreviewHeadersParams): Promise<IPreviewHeadersResult> => {
  const { file, ...restParams } = params;
  const formData = new FormData();
  formData.append('file', file);
  Object.keys(restParams).forEach((key) => {
    const value = (restParams as any)[key];
    if (value !== undefined && value !== null) {
      formData.append(key, String(value));
    }
  });

  return fetch(`${window._BaseURL}/api/import/preview_headers`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  })
    .then((res) => res.json())
    .then((res) => {
      if (res.success) {
        return res.data;
      }
      throw new Error(res.errorMessage || 'Preview failed');
    });
};

const importData = (params: IImportDataParams): Promise<number> => {
  const { file, ...restParams } = params;
  const formData = new FormData();
  formData.append('file', file);
  Object.keys(restParams).forEach((key) => {
    const value = (restParams as any)[key];
    if (value !== undefined && value !== null) {
      formData.append(key, String(value));
    }
  });

  return fetch(`${window._BaseURL}/api/import/import_data`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  })
    .then((res) => res.json())
    .then((res) => {
      if (res.success) {
        return res.data;
      }
      throw new Error(res.errorMessage || 'Import failed');
    });
};

const executeSqlFile = (params: IExecuteSqlFileParams): Promise<number> => {
  const { file, ...restParams } = params;
  const formData = new FormData();
  formData.append('file', file);
  Object.keys(restParams).forEach((key) => {
    const value = (restParams as any)[key];
    if (value !== undefined && value !== null) {
      formData.append(key, String(value));
    }
  });

  return fetch(`${window._BaseURL}/api/sql/execute_file`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  })
    .then((res) => res.json())
    .then((res) => {
      if (res.success) {
        return res.data;
      }
      throw new Error(res.errorMessage || 'Execute SQL failed');
    });
};

const subscribeTask = ({ id, onMessage, onError, fallbackInterval = 1000 }: ISubscribeTaskParams) => {
  let stopped = false;
  let pollingTimer: NodeJS.Timeout | null = null;
  let eventSource: EventSource | null = null;

  const stopPolling = () => {
    if (pollingTimer) {
      clearInterval(pollingTimer);
      pollingTimer = null;
    }
  };

  const stop = () => {
    stopped = true;
    stopPolling();
    eventSource?.close();
    eventSource = null;
  };

  const handleTask = (task: ITask) => {
    if (stopped) {
      return;
    }
    onMessage(task);
    if (task.taskStatus === 'FINISH' || task.taskStatus === 'ERROR') {
      stop();
    }
  };

  const startPolling = () => {
    stopPolling();
    pollingTimer = setInterval(async () => {
      try {
        handleTask(await getTask({ id }));
      } catch (error) {
        stop();
        onError?.(error);
      }
    }, fallbackInterval);
  };

  if (typeof EventSource === 'undefined') {
    startPolling();
    return stop;
  }

  eventSource = new EventSource(`${window._BaseURL}/api/task/subscribe/${id}`, { withCredentials: true });
  eventSource.addEventListener('task', (event) => {
    handleTask(JSON.parse(event.data));
  });
  eventSource.onerror = () => {
    if (stopped) {
      return;
    }
    eventSource?.close();
    eventSource = null;
    startPolling();
  };

  return stop;
};

export default {
  exportResultData,
  exportSchemaDoc,
  importData,
  executeSqlFile,
  getTask,
  subscribeTask,
  getTaskList,
  cleanupTasks,
  previewFileHeaders,
  transferData,
};

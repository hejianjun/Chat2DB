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
  taskProgress: string;
  taskName: string;
  downloadUrl: string;
  content?: string;
}

export interface IExportResultDataParams {
  sql: string;
  originalSql: string;
  exportType: string;
  exportSize: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
}

export interface IImportDataParams {
  file: File;
  tableName: string;
  fileType: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
}

const exportResultData = createRequest<IExportResultDataParams, number>('/api/export/export_data', { method: 'post' });
const getTask = createRequest<{ id: number }, ITask>('/api/task/get/:id', { method: 'get' });

const importData = (params: IImportDataParams) => {
  const { file, ...restParams } = params;
  const formData = new FormData();
  formData.append('file', file);
  Object.keys(restParams).forEach((key) => {
    const value = (restParams as any)[key];
    if (value !== undefined && value !== null) {
      formData.append(key, String(value));
    }
  });
  
  return fetch('/api/import/import_data', {
    method: 'POST',
    credentials: 'include',
    body: formData,
  }).then((res) => res.json()).then((res) => {
    if (res.success) {
      return res.data;
    }
    throw new Error(res.errorMessage || 'Import failed');
  });
};

export default {
  exportResultData,
  importData,
  getTask,
};
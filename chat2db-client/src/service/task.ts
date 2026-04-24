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

const exportResultData = createRequest<IExportResultDataParams, number>('/api/export/export_data', { method: 'post' });
const getTask = createRequest<{ id: number }, ITask>('/api/task/get/:id', { method: 'get' });

export default {
  exportResultData,
  getTask,
};
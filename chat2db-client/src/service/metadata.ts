import createRequest from './base';

export type MetadataCommentObjectType = 'DATABASE' | 'SCHEMA' | 'REDIS_PREFIX' | 'REDIS_KEY';

export interface IMetadataCommentSaveParams {
  dataSourceId: number;
  objectType: MetadataCommentObjectType;
  databaseName?: string;
  schemaName?: string;
  objectName: string;
  comment?: string;
  aiComment?: string;
}

const saveComment = createRequest<IMetadataCommentSaveParams, void>('/api/metadata/comment/save', {
  method: 'post',
});

export default {
  saveComment,
};

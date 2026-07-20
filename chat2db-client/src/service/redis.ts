import createRequest from './base';
import { createSSEConnection } from '@/utils/sse';
import { formatParams } from '@/utils/url';

export interface IRedisKeyListParams {
  dataSourceId: number;
  databaseName?: string;
  searchKey?: string;
  cursor?: string;
  count?: number;
}

export interface IRedisKeyItem {
  name: string;
  value?: any;
  type?: string;
  ttl?: number;
  size?: number;
}

export interface IRedisKeyPrefixItem {
  name: string;
  comment?: string;
  aiComment?: string;
  keyCount?: number;
  sampleTypes?: string;
  building?: boolean;
}

export interface IRedisKeyValuePage extends Omit<IRedisKeyItem, 'value'> {
  items: Array<Record<string, any>>;
  nextCursor: string;
  hasMore: boolean;
  total?: number;
  fullyLoaded: boolean;
}

export interface IRedisKeyPrefixSearchParams {
  dataSourceId: number;
  databaseName?: string;
  searchKey?: string;
  refresh?: boolean;
  sortField?: 'name' | 'keyCount';
  sortOrder?: 'ascend' | 'descend';
}

export interface IRedisKeyPrefixCommentUpdateParams {
  dataSourceId: number;
  databaseName?: string;
  prefix: string;
  comment: string;
}

export interface IRedisKeyPrefixRefreshParams {
  dataSourceId: number;
  databaseName?: string;
}

export interface IRedisKeyPrefixBuildStatusParams {
  dataSourceId: number;
  databaseName?: string;
}

export interface IRedisKeyPrefixBuildStatus {
  status?: 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED' | string;
  cursor?: string;
  scannedCount?: number;
  prefixCount?: number;
  startedAt?: string;
  updatedAt?: string;
  errorMessage?: string;
}

export interface IRedisKeyPrefixSampleParams {
  dataSourceId: number;
  databaseName?: string;
  prefix: string;
}

export interface IRedisKeyQueryParams {
  dataSourceId: number;
  databaseName?: string;
  keyName: string;
}

export interface IRedisKeyValuePageParams extends IRedisKeyQueryParams {
  cursor?: string;
  count?: number;
}

export interface IRedisKeyUpdateParams {
  dataSourceId: number;
  databaseName?: string;
  originalKey: string;
  updateKey: string;
  keyType: string;
  value: any;
  updateTtl?: number;
  expectedElementCount?: number;
}

export interface IRedisKeyPartialUpdateParams {
  dataSourceId: number;
  databaseName?: string;
  keyName: string;
  keyType: string;
  addedFields?: Record<string, string>;
  removedFields?: string[];
  hashUpserts?: Record<string, string>;
  hashDeletes?: string[];
  listUpdates?: Record<number, string>;
  listAppends?: string[];
  setAdds?: string[];
  setRemoves?: string[];
  zsetUpserts?: Record<string, number>;
  zsetRemoves?: string[];
  updateKey?: string;
  updateTtl?: number;
}

export interface IRedisKeyCreateParams {
  dataSourceId: number;
  databaseName?: string;
  name: string;
  keyType: string;
  value: any;
  ttl?: number;
}

export interface IRedisKeyDeleteParams {
  dataSourceId: number;
  databaseName?: string;
  keyName: string;
}

export interface IRedisMonitorStreamOptions {
  dataSourceId: number;
  databaseName?: string;
  uid: string;
  onCommand: (line: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

export interface IRedisKeyStreamOptions extends IRedisKeyListParams {
  uid: string;
  onBatch: (items: IRedisKeyItem[], total: number, cursor: string, hasMore: boolean) => void;
  onDone: (total: number, cursor: string, hasMore: boolean) => void;
  onError: (message: string) => void;
}

const getKeyList = createRequest<IRedisKeyListParams, IRedisKeyItem[]>('/api/redis/key/list', {
  method: 'get',
  delayTime: 200,
});

const queryKey = createRequest<IRedisKeyQueryParams, IRedisKeyItem>('/api/redis/key/query', {
  method: 'get',
});

const searchKeyPrefixes = createRequest<IRedisKeyPrefixSearchParams, IRedisKeyPrefixItem[]>(
  '/api/redis/key-prefix/search',
  {
    method: 'get',
  },
);

const sampleKeyPrefixTypes = createRequest<IRedisKeyPrefixSampleParams, IRedisKeyItem[]>('/api/redis/key-prefix/sample', {
  method: 'get',
});

const getKeyValuePage = createRequest<IRedisKeyValuePageParams, IRedisKeyValuePage>('/api/redis/key/value-page', {
  method: 'get',
});

const updateKeyPrefixComment = createRequest<IRedisKeyPrefixCommentUpdateParams, void>(
  '/api/redis/key-prefix/comment',
  {
    method: 'post',
  },
);

const refreshKeyPrefixCache = createRequest<IRedisKeyPrefixRefreshParams, void>('/api/redis/key-prefix/refresh', {
  method: 'post',
});

const getKeyPrefixBuildStatus = createRequest<IRedisKeyPrefixBuildStatusParams, IRedisKeyPrefixBuildStatus>(
  '/api/redis/key-prefix/status',
  {
    method: 'get',
  },
);

const updateKey = createRequest<IRedisKeyUpdateParams, void>('/api/redis/key/update', {
  method: 'post',
});

const partialUpdateKey = createRequest<IRedisKeyPartialUpdateParams, void>('/api/redis/key/partial-update', {
  method: 'post',
});

const createKey = createRequest<IRedisKeyCreateParams, void>('/api/redis/key/create', {
  method: 'post',
});

const deleteKey = createRequest<IRedisKeyDeleteParams, void>('/api/redis/key/delete', {
  method: 'post',
});

const streamMonitor = (options: IRedisMonitorStreamOptions) => {
  const { uid, onCommand, onDone, onError, ...params } = options;
  const url = `/api/redis/monitor/stream?${formatParams(params)}`;
  const eventSource = createSSEConnection({ url, uid });

  eventSource.addEventListener('command', (event: any) => {
    try {
      const data = event.data ? JSON.parse(event.data) : {};
      onCommand(data.line || '');
    } catch {
      onError('Redis monitor 数据解析失败');
      eventSource.close();
    }
  });

  eventSource.addEventListener('done', () => {
    onDone();
    eventSource.close();
  });

  eventSource.addEventListener('redis_error', (event: any) => {
    try {
      const data = event.data ? JSON.parse(event.data) : {};
      onError(data.message || 'Redis monitor 连接失败');
    } catch {
      onError('Redis monitor 连接失败');
    } finally {
      eventSource.close();
    }
  });

  eventSource.addEventListener('error', () => {
    onError('Redis monitor 连接已断开');
    eventSource.close();
  });

  return () => {
    eventSource.close();
  };
};

const streamKeyList = (options: IRedisKeyStreamOptions) => {
  const { uid, onBatch, onDone, onError, ...params } = options;
  const url = `/api/redis/key/stream?${formatParams(params)}`;
  const eventSource = createSSEConnection({ url, uid });

  eventSource.addEventListener('keys', (event: any) => {
    try {
      const data = JSON.parse(event.data);
      const items = Array.isArray(data) ? data : data.items || [];
      onBatch(items, 0, '0', false);
    } catch {
      onError('Redis key 数据解析失败');
      eventSource.close();
    }
  });

  eventSource.addEventListener('done', (event: any) => {
    try {
      const data = JSON.parse(event.data);
      onDone(data.total || 0, data.cursor || '0', Boolean(data.hasMore));
    } catch {
      onDone(0, '0', false);
    } finally {
      eventSource.close();
    }
  });

  eventSource.addEventListener('redis_error', (event: any) => {
    try {
      const data = event.data ? JSON.parse(event.data) : {};
      onError(data.message || 'Redis key 加载失败');
    } catch {
      onError('Redis key 加载失败');
    } finally {
      eventSource.close();
    }
  });

  eventSource.addEventListener('error', () => {
    onError('Redis key 加载连接失败');
    eventSource.close();
  });

  return () => {
    eventSource.close();
  };
};

export default {
  createKey,
  deleteKey,
  getKeyPrefixBuildStatus,
  getKeyValuePage,
  getKeyList,
  partialUpdateKey,
  queryKey,
  refreshKeyPrefixCache,
  searchKeyPrefixes,
  sampleKeyPrefixTypes,
  streamKeyList,
  streamMonitor,
  updateKeyPrefixComment,
  updateKey,
};

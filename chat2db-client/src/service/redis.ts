import createRequest from './base';
import { createSSEConnection } from '@/utils/sse';
import { formatParams } from '@/utils/url';

export interface IRedisKeyListParams {
  dataSourceId: number;
  databaseName?: string;
  searchKey?: string;
  count?: number;
}

export interface IRedisKeyItem {
  name: string;
  value?: string;
  type?: string;
  ttl?: number;
  size?: number;
}

export interface IRedisKeyStreamOptions extends IRedisKeyListParams {
  batchSize?: number;
  uid: string;
  onBatch: (items: IRedisKeyItem[], total: number) => void;
  onDone: (total: number) => void;
  onError: (message: string) => void;
}

const getKeyList = createRequest<IRedisKeyListParams, IRedisKeyItem[]>('/api/redis/key/list', {
  method: 'get',
  delayTime: 200,
});

const streamKeyList = (options: IRedisKeyStreamOptions) => {
  const { uid, onBatch, onDone, onError, ...params } = options;
  const url = `/api/redis/key/stream?${formatParams(params)}`;
  const eventSource = createSSEConnection({ url, uid });

  eventSource.addEventListener('keys', (event: any) => {
    try {
      const data = JSON.parse(event.data);
      onBatch(data.items || [], data.total || 0);
    } catch {
      onError('Redis key 数据解析失败');
      eventSource.close();
    }
  });

  eventSource.addEventListener('done', (event: any) => {
    try {
      const data = JSON.parse(event.data);
      onDone(data.total || 0);
    } catch {
      onDone(0);
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
  getKeyList,
  streamKeyList,
};

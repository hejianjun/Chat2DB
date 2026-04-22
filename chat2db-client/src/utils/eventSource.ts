import { EventSourcePolyfill } from 'event-source-polyfill';
import { ChatStateType } from '@/pages/main/workspace/store/aiChatStore';

interface EventSourceOptions {
  url: string;
  uid: string;
  onOpen?: () => void;
  onMessage?: (content: string, thinking?: string) => void;
  onStateChange?: (state: ChatStateType, message?: string) => void;
  onError?: (error: string) => void;
  onDone?: () => void;
  onTablesSelected?: (tables: string[]) => void;
  onSchemaFetched?: (ddl: string) => void;
}

const getSSEBaseUrl = (): string => {
  const storedBaseURL = localStorage.getItem('_BaseURL');
  if (storedBaseURL) {
    return storedBaseURL;
  }
  if (location.href.indexOf('dist/index.html') > -1) {
    return `http://127.0.0.1:${__APP_PORT__ || '10824'}`;
  }
  const isDev = process.env.NODE_ENV === 'development';
  if (isDev) {
    return 'http://127.0.0.1:10821';
  }
  return location.origin;
};

const connectToEventSource = (options: EventSourceOptions): (() => void) => {
  const {
    url,
    uid,
    onOpen,
    onMessage,
    onStateChange,
    onError,
    onDone,
    onTablesSelected,
    onSchemaFetched,
  } = options;

  if (!url) {
    throw new Error('url is required');
  }

  const DBHUB = localStorage.getItem('DBHUB');
  const sseBaseUrl = getSSEBaseUrl();
  console.log('[SSE] Connecting to:', `${sseBaseUrl}${url}`, 'uid:', uid);
  const eventSource = new EventSourcePolyfill(`${sseBaseUrl}${url}`, {
    headers: {
      uid,
      DBHUB: DBHUB || '',
    },
    heartbeatTimeout: 12000000,
  });

  eventSource.addEventListener('open', () => {
    console.log('[SSE] Connection opened');
    onOpen?.();
  });

  eventSource.addEventListener('message', (event: any) => {
    console.log('[SSE] Message received:', event.data);
    const data = event.data;
    if (data === '[DONE]') {
      console.log('[SSE] Stream completed');
      onDone?.();
      eventSource.close();
      return;
    }
    try {
      const parsed = JSON.parse(data);
      if (parsed.content || parsed.thinking) {
        onMessage?.(parsed.content, parsed.thinking);
      }
    } catch {
      onMessage?.(data);
    }
  });

  eventSource.addEventListener('state', (event: any) => {
    console.log('[SSE] State event:', event.data);
    try {
      const { state, message } = JSON.parse(event.data);
      onStateChange?.(state as ChatStateType, message);
    } catch (e) {
      console.error('Failed to parse state event', e);
    }
  });

  eventSource.addEventListener('error', (e) => {
    console.error('[SSE] Error:', e);
    onError?.('Connection error');
    eventSource.close();
  });

  eventSource.addEventListener('tables_selected', (event: any) => {
    try {
      const { tables } = JSON.parse(event.data);
      onTablesSelected?.(tables);
    } catch (e) {
      console.error('Failed to parse tables_selected event', e);
    }
  });

  eventSource.addEventListener('schema_fetched', (event: any) => {
    try {
      const { ddl } = JSON.parse(event.data);
      onSchemaFetched?.(ddl);
    } catch (e) {
      console.error('Failed to parse schema_fetched event', e);
    }
  });

  return () => {
    eventSource.close();
  };
};

const cancelChatSession = async (sessionId: string): Promise<void> => {
  const DBHUB = localStorage.getItem('DBHUB');
  const sseBaseUrl = getSSEBaseUrl();
  await fetch(`${sseBaseUrl}/api/ai/chat/${sessionId}`, {
    method: 'DELETE',
    headers: {
      DBHUB: DBHUB || '',
    },
  });
};

export { cancelChatSession };

export default connectToEventSource;

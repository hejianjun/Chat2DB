import { EventSourcePolyfill } from 'event-source-polyfill';
import { ChatStateType } from '@/pages/main/workspace/store/aiChatStore';

interface EventSourceOptions {
  url: string;
  uid: string;
  onOpen?: () => void;
  onMessage?: (content: string) => void;
  onStateChange?: (state: ChatStateType, message?: string) => void;
  onError?: (error: string) => void;
  onDone?: () => void;
  onTablesSelected?: (tables: string[]) => void;
  onSchemaFetched?: (ddl: string) => void;
}

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
  const eventSource = new EventSourcePolyfill(`${window._BaseURL}${url}`, {
    headers: {
      uid,
      DBHUB: DBHUB || '',
    },
  });

  eventSource.addEventListener('open', () => {
    onOpen?.();
  });

  eventSource.addEventListener('message', (event: MessageEvent) => {
    const data = event.data;
    if (data === '[DONE]') {
      onDone?.();
      return;
    }
    try {
      const parsed = JSON.parse(data);
      if (parsed.content) {
        onMessage?.(parsed.content);
      }
    } catch {
      onMessage?.(data);
    }
  });

  eventSource.addEventListener('state', (event: MessageEvent) => {
    try {
      const { state, message } = JSON.parse(event.data);
      onStateChange?.(state as ChatStateType, message);
    } catch (e) {
      console.error('Failed to parse state event', e);
    }
  });

  eventSource.addEventListener('error', () => {
    onError?.('Connection error');
    eventSource.close();
  });

  eventSource.addEventListener('tables_selected', (event: MessageEvent) => {
    try {
      const { tables } = JSON.parse(event.data);
      onTablesSelected?.(tables);
    } catch (e) {
      console.error('Failed to parse tables_selected event', e);
    }
  });

  eventSource.addEventListener('schema_fetched', (event: MessageEvent) => {
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
  await fetch(`${window._BaseURL}/api/ai/chat/${sessionId}`, {
    method: 'DELETE',
    headers: {
      DBHUB: DBHUB || '',
    },
  });
};

export { cancelChatSession };

export default connectToEventSource;
import { v4 as uuidv4 } from 'uuid';
import { cancelSSESession, createSSEConnection } from '@/utils/sse';
import { formatParams } from '@/utils/url';
import { IBoundInfo } from '@/typings';

export interface IAiSqlCompletionParams {
  boundInfo: IBoundInfo;
  message: string;
  tableNames?: string[];
  ext?: string;
}

export interface IAiSqlCompletionTask {
  promise: Promise<string>;
  cancel: () => void;
}

const extractSqlFromContent = (content: string): string => {
  const trimmedContent = content.trim();
  const databaseCodeBlockMatch = trimmedContent.match(/```(?:sql|mql|mongodb|javascript|js)\s*([\s\S]*?)```/i);
  if (databaseCodeBlockMatch?.[1]) {
    return databaseCodeBlockMatch[1].trim();
  }

  const codeBlockMatch = trimmedContent.match(/```\s*([\s\S]*?)```/);
  if (codeBlockMatch?.[1]) {
    return codeBlockMatch[1].trim();
  }

  return trimmedContent
    .replace(/^(?:SQL|MQL)\s*[:：]\s*/i, '')
    .replace(/^完整\s*(?:SQL|MQL)\s*[:：]\s*/i, '')
    .trim();
};

const extractSqlComments = (sql: string): string[] => {
  const comments: string[] = [];
  let quote: "'" | '"' | '`' | null = null;

  for (let i = 0; i < sql.length; i++) {
    const char = sql[i];
    const next = sql[i + 1];

    if (quote) {
      if (char === quote) {
        if (next === quote) {
          i++;
        } else {
          quote = null;
        }
      } else if (char === '\\') {
        i++;
      }
      continue;
    }

    if (char === "'" || char === '"' || char === '`') {
      quote = char;
      continue;
    }

    if (char === '-' && next === '-') {
      const end = sql.slice(i).search(/[\r\n]/);
      const commentEnd = end === -1 ? sql.length : i + end;
      comments.push(sql.slice(i, commentEnd).trimEnd());
      i = commentEnd - 1;
      continue;
    }

    if (char === '/' && next === '*') {
      const end = sql.indexOf('*/', i + 2);
      const commentEnd = end === -1 ? sql.length : end + 2;
      comments.push(sql.slice(i, commentEnd).trimEnd());
      i = commentEnd - 1;
    }
  }

  return comments;
};

export const restoreMissingSqlComments = (originalSql: string, completedSql: string): string => {
  const missingComments = extractSqlComments(originalSql).filter((comment) => !completedSql.includes(comment));
  return missingComments.length > 0 ? `${missingComments.join('\n')}\n${completedSql}` : completedSql;
};

export const requestAiSqlCompletion = (params: IAiSqlCompletionParams): IAiSqlCompletionTask => {
  const sessionId = uuidv4();
  const query = formatParams({
    conversationId: sessionId,
    message: params.message,
    promptType: params.boundInfo.databaseType === 'MONGODB' ? 'MQL_COMPLETION' : 'SQL_COMPLETION',
    dataSourceId: params.boundInfo.dataSourceId,
    databaseName: params.boundInfo.databaseName,
    schemaName: params.boundInfo.schemaName,
    tableNames: params.tableNames,
    ext: params.ext,
  });
  const eventSource = createSSEConnection({ url: `/api/ai/chat?${query}`, uid: sessionId });

  let settled = false;
  let content = '';
  let rejectPromise: ((reason?: any) => void) | null = null;

  const cleanup = () => {
    eventSource.close();
  };

  const promise = new Promise<string>((resolve, reject) => {
    rejectPromise = reject;

    eventSource.addEventListener('message', (event: any) => {
      const data = event.data;
      if (data === '[DONE]') {
        settled = true;
        cleanup();
        resolve(extractSqlFromContent(content));
        return;
      }

      try {
        const parsed = JSON.parse(data);
        if (parsed.content) {
          content += parsed.content;
        }
      } catch {
        content += data;
      }
    });

    eventSource.addEventListener('error', () => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(new Error('AI SQL completion failed'));
    });
  });

  return {
    promise,
    cancel: () => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      rejectPromise?.(new Error('AI SQL completion cancelled'));
      cancelSSESession(sessionId);
    },
  };
};

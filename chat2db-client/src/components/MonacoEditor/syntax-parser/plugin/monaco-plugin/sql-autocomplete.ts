import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import { monacoSqlAutocomplete } from './index';
import sqlService from '@/service/sql';
import { IBoundInfo } from '@/typings/workspace';
import { ICompletionItem, ITableInfo, ICursorInfo } from '../sql-parser/base/define';

export interface ISqlAutocompleteOptions {
  monaco: typeof monaco;
  editor: monaco.editor.IStandaloneCodeEditor;
  boundInfo: IBoundInfo;
  parserType?: 'mysql' | 'odps' | 'blink' | 'dsql' | 'grail' | 'emcsql';
}

export interface ISqlAutocompleteDisposable {
  dispose: () => void;
}

// 字段缓存
const fieldCache = new Map<string, any[]>();

const mapDatabaseTypeToParser = (databaseType: string): ISqlAutocompleteOptions['parserType'] => {
  const typeMap: Record<string, ISqlAutocompleteOptions['parserType']> = {
    MYSQL: 'mysql',
    POSTGRESQL: 'mysql',
    ORACLE: 'mysql',
    SQLSERVER: 'mysql',
    H2: 'mysql',
    MARIADB: 'mysql',
    TIDB: 'mysql',
    DAMENG: 'mysql',
    KINGBASE: 'mysql',
    VERTICAL: 'mysql',
    SQLITE: 'mysql',
    PRESTO: 'mysql',
    TRINO: 'mysql',
    CLICKHOUSE: 'mysql',
    DB2: 'mysql',
    SYBASE: 'mysql',
    INFLUXDB: 'mysql',
    MONGODB: 'mysql',
    REDIS: 'mysql',
    ELASTICSEARCH: 'mysql',
    HIVE: 'mysql',
    SPARK: 'mysql',
    IMPALA: 'mysql',
    KYLESE: 'mysql',
    OCEANBASE: 'mysql',
    GAUSS: 'mysql',
    TDENGINE: 'mysql',
    TABLESTORE: 'mysql',
  };
  return typeMap[databaseType?.toUpperCase()] || 'mysql';
};

export const initSqlAutocomplete = (options: ISqlAutocompleteOptions): ISqlAutocompleteDisposable => {
  const { monaco, editor, boundInfo, parserType } = options;
  
  const effectiveParserType = parserType || mapDatabaseTypeToParser(boundInfo.databaseType || 'MYSQL');

  // Register completion provider and store the disposable
  const completionProviderDisposable = monacoSqlAutocomplete(monaco, editor, {
    language: 'sql',
    parserType: effectiveParserType,
    
    onParse: (parseResult) => {
      if (parseResult.error && parseResult.error.reason === 'incomplete') {
      }
    },

    onSuggestTableNames: async (cursorInfo?: ICursorInfo<ITableInfo>) => {
      // console.log('[SQL 补全 - API] 获取表名列表，boundInfo:', {
      //   dataSourceId: boundInfo.dataSourceId,
      //   databaseName: boundInfo.databaseName,
      //   schemaName: boundInfo.schemaName,
      // });
      
      try {
        const data = await sqlService.getAllTableList({
          dataSourceId: boundInfo.dataSourceId,
          databaseName: boundInfo.databaseName,
          schemaName: boundInfo.schemaName,
        });
        
        // console.log('[SQL 补全 - API] 获取到表名数量:', data.length);
        
        return data.map((table) => {
          const name = table.name;
          return {
            label: name,
            insertText: name,
            sortText: `Z${name}`,  // 表名排在最后（Z 开头）
            kind: monaco.languages.CompletionItemKind.Folder as any,
            detail: table.comment || '',
          };
        });
      } catch (error) {
        console.error('[SQL 补全 - API] 获取表名失败:', error);
        return [];
      }
    },

    onSuggestTableFields: async (tableInfo?: ITableInfo, cursorValue?: string, rootStatement?: any) => {
      const tableName = tableInfo?.tableName?.value;
      // console.log('[SQL 补全 - API] 获取表字段，表信息:', {
      //   tableName,
      //   namespace: tableInfo?.namespace?.value,
      //   cursorValue,
      // });
      
      // 检查缓存
      const cacheKey = `${boundInfo.dataSourceId}_${boundInfo.databaseName || ''}_${boundInfo.schemaName || ''}_${tableName}`;
      if (fieldCache.has(cacheKey)) {
        // console.log('[SQL 补全 - API] 使用缓存字段:', cacheKey);
        const cachedData = fieldCache.get(cacheKey);
        return cachedData.map((column) => ({
          label: column.name,
          insertText: column.name,
          sortText: `B${column.name}`,
          kind: monaco.languages.CompletionItemKind.Field as any,
          detail: column.columnType || column.dataType,
          documentation: column.comment || '',
        }));
      }
      
      try {
        if (!tableName) {
          console.warn('[SQL 补全 - API] 表名为空，返回空数组');
          return [];
        }

        const data = await sqlService.getColumnList({
          dataSourceId: boundInfo.dataSourceId,
          databaseName: boundInfo.databaseName || '',
          schemaName: boundInfo.schemaName,
          tableName,
        });
        
        // console.log('[SQL 补全 - API] 获取到字段数量:', data.length);
        // console.log('[SQL 补全 - API] 字段示例:', data.slice(0, 3));
        
        // 缓存结果
        fieldCache.set(cacheKey, data);
        
        return data.map((column) => {
          const name = column.name;
          return {
            label: name,
            insertText: name,
            sortText: `B${name}`,
            kind: monaco.languages.CompletionItemKind.Field as any,
            detail: column.columnType || column.dataType,
            documentation: column.comment || '',
          };
        });
      } catch (error) {
        console.error('[SQL 补全 - API] 获取表字段失败:', error);
        return [];
      }
    },

    onSuggestFunctionName: async (inputValue?: string) => {
      const commonFunctions = [
        'COUNT', 'SUM', 'AVG', 'MAX', 'MIN',
        'SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'INNER JOIN',
        'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT',
        'AND', 'OR', 'NOT', 'IN', 'EXISTS', 'BETWEEN', 'LIKE',
        'AS', 'DISTINCT', 'NULL', 'IS NULL', 'IS NOT NULL',
        'ASC', 'DESC', 'NULLS FIRST', 'NULLS LAST',
        'UNION', 'UNION ALL', 'INTERSECT', 'EXCEPT',
        'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
        'CAST', 'CONVERT', 'TRIM', 'SUBSTRING', 'LENGTH',
        'UPPER', 'LOWER', 'CONCAT', 'REPLACE',
        'DATE', 'TIME', 'TIMESTAMP', 'EXTRACT',
        'COALESCE', 'IFNULL', 'NULLIF',
      ];
      
      return commonFunctions.map((func) => ({
        label: func,
        insertText: func,
        sortText: `C${func}`,
        kind: monaco.languages.CompletionItemKind.Function as any,
      }));
    },

    onSuggestFieldGroup: (tableNameOrAlias?: string) => {
      const label = tableNameOrAlias || '';
      return {
        label,
        insertText: label,
        sortText: `D${label}`,
        kind: monaco.languages.CompletionItemKind.Folder as any,
      };
    },

    onHoverTableField: async (fieldName?: string, extra?: ICompletionItem) => {
      const docs: { value: string }[] = [];
      if (fieldName) {
        docs.push({ value: `**Field:** ${fieldName}` });
      }
      if (extra?.detail) {
        docs.push({ value: `**Type:** ${extra.detail}` });
      }
      if (extra?.documentation) {
        docs.push({ value: `**Comment:** ${extra.documentation}` });
      }
      return docs;
    },

    onHoverTableName: async (cursorInfo?: ICursorInfo) => {
      return [
        { value: `**Table:** ${cursorInfo?.token?.value || ''}` },
      ];
    },

    onHoverFunctionName: async (functionName?: string) => {
      return [
        { value: `**Function:** ${functionName || ''}` },
      ];
    },
  });

  return {
    dispose: () => {
      // Dispose the completion provider to avoid duplicate registrations
      if (completionProviderDisposable) {
        completionProviderDisposable.dispose();
      }
      // 清理缓存（可选，根据 boundInfo 清理或不清理）
      // fieldCache.clear();
    },
  };
};

// 缓存清理函数
export const clearFieldCache = () => {
  fieldCache.clear();
  console.log('[SQL 补全 - API] 缓存已清理');
};

export const disposeSqlAutocomplete = (disposable?: ISqlAutocompleteDisposable) => {
  if (disposable) {
    disposable.dispose();
  }
};

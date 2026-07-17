import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import sqlService from '@/service/sql';
import { IBoundInfo } from '@/typings/workspace';

import type { ISqlAutocompleteDisposable, ISqlAutocompleteOptions } from './sql-autocomplete';

interface IMongodbCompletionDefinition {
  name: string;
  snippet?: string;
  detail: string;
  documentation?: string;
}

interface IMongodbCursorContext {
  inputValue: string;
  range: monaco.Range;
  textBeforeCursor: string;
}

const mongodbFieldCache = new Map<string, any[]>();

const databaseMethods: IMongodbCompletionDefinition[] = [
  {
    name: 'runCommand',
    snippet: 'runCommand({ ${1:ping}: ${2:1} })',
    detail: '(MongoDB 数据库方法)',
    documentation: '执行原生 MongoDB 数据库命令',
  },
  {
    name: 'createCollection',
    snippet: "createCollection('${1:collection}')",
    detail: '(MongoDB 数据库方法)',
  },
  { name: 'dropDatabase', snippet: 'dropDatabase()', detail: '(MongoDB 数据库方法)' },
  {
    name: 'getCollection',
    snippet: "getCollection('${1:collection}')",
    detail: '(MongoDB 数据库方法)',
    documentation: '用于访问名称不能写成 JavaScript 标识符的集合',
  },
];

const collectionMethods: IMongodbCompletionDefinition[] = [
  { name: 'find', snippet: 'find({ ${1} })', detail: '(MongoDB 查询方法)' },
  { name: 'findOne', snippet: 'findOne({ ${1} })', detail: '(MongoDB 查询方法)' },
  { name: 'aggregate', snippet: 'aggregate([\n\t${1}\n])', detail: '(MongoDB 聚合方法)' },
  { name: 'countDocuments', snippet: 'countDocuments({ ${1} })', detail: '(MongoDB 查询方法)' },
  {
    name: 'insertOne',
    snippet: 'insertOne({\n\t${1:field}: ${2:value}\n})',
    detail: '(MongoDB 写入方法)',
  },
  {
    name: 'insertMany',
    snippet: 'insertMany([\n\t{ ${1:field}: ${2:value} }\n])',
    detail: '(MongoDB 写入方法)',
  },
  {
    name: 'updateOne',
    snippet: 'updateOne({ ${1:filter} }, { \\$set: { ${2:field}: ${3:value} } })',
    detail: '(MongoDB 更新方法)',
  },
  {
    name: 'updateMany',
    snippet: 'updateMany({ ${1:filter} }, { \\$set: { ${2:field}: ${3:value} } })',
    detail: '(MongoDB 更新方法)',
  },
  {
    name: 'replaceOne',
    snippet: 'replaceOne({ ${1:filter} }, { ${2:field}: ${3:value} })',
    detail: '(MongoDB 更新方法)',
  },
  { name: 'deleteOne', snippet: 'deleteOne({ ${1} })', detail: '(MongoDB 删除方法)' },
  { name: 'deleteMany', snippet: 'deleteMany({ ${1} })', detail: '(MongoDB 删除方法)' },
  {
    name: 'createIndex',
    snippet: 'createIndex({ ${1:field}: ${2:1} })',
    detail: '(MongoDB 索引方法)',
  },
  { name: 'dropIndex', snippet: "dropIndex('${1:indexName}')", detail: '(MongoDB 索引方法)' },
  { name: 'drop', snippet: 'drop()', detail: '(MongoDB 集合方法)' },
];

const cursorMethods: IMongodbCompletionDefinition[] = [
  { name: 'sort', snippet: 'sort({ ${1:field}: ${2:-1} })', detail: '(MongoDB 游标方法)' },
  { name: 'projection', snippet: 'projection({ ${1:field}: ${2:1} })', detail: '(MongoDB 游标方法)' },
  { name: 'skip', snippet: 'skip(${1:0})', detail: '(MongoDB 游标方法)' },
  { name: 'limit', snippet: 'limit(${1:200})', detail: '(MongoDB 游标方法)' },
];

const queryOperators = [
  '$eq',
  '$ne',
  '$gt',
  '$gte',
  '$lt',
  '$lte',
  '$in',
  '$nin',
  '$exists',
  '$regex',
  '$and',
  '$or',
  '$not',
  '$nor',
  '$elemMatch',
  '$all',
  '$size',
  '$type',
];

const updateOperators = [
  '$set',
  '$unset',
  '$inc',
  '$mul',
  '$rename',
  '$min',
  '$max',
  '$currentDate',
  '$push',
  '$addToSet',
  '$pop',
  '$pull',
  '$pullAll',
];

const aggregationStages = [
  '$match',
  '$project',
  '$group',
  '$sort',
  '$skip',
  '$limit',
  '$unwind',
  '$lookup',
  '$count',
  '$addFields',
  '$set',
  '$unset',
  '$replaceRoot',
  '$replaceWith',
];

const bsonHelpers: IMongodbCompletionDefinition[] = [
  { name: 'ObjectId', snippet: "ObjectId('${1:id}')", detail: '(MongoDB BSON 类型)' },
  { name: 'ISODate', snippet: "ISODate('${1:date}')", detail: '(MongoDB BSON 类型)' },
  { name: 'NumberLong', snippet: "NumberLong('${1:value}')", detail: '(MongoDB BSON 类型)' },
];

const rootDefinitions: IMongodbCompletionDefinition[] = [
  { name: 'db', snippet: 'db.', detail: '(MongoDB 当前数据库)' },
  ...bsonHelpers,
];

const runCommandSnippets: IMongodbCompletionDefinition[] = [
  { name: 'ping', snippet: 'ping: 1', detail: '(MongoDB 命令)' },
  {
    name: 'find',
    snippet: "find: '${1:collection}', filter: { ${2} }, limit: ${3:200}",
    detail: '(MongoDB 命令)',
  },
  {
    name: 'aggregate',
    snippet: "aggregate: '${1:collection}', pipeline: [${2}], cursor: {}",
    detail: '(MongoDB 命令)',
  },
  { name: 'count', snippet: "count: '${1:collection}', query: { ${2} }", detail: '(MongoDB 命令)' },
  { name: 'insert', snippet: "insert: '${1:collection}', documents: [${2}]", detail: '(MongoDB 命令)' },
  { name: 'update', snippet: "update: '${1:collection}', updates: [${2}]", detail: '(MongoDB 命令)' },
  { name: 'delete', snippet: "delete: '${1:collection}', deletes: [${2}]", detail: '(MongoDB 命令)' },
  { name: 'create', snippet: "create: '${1:collection}'", detail: '(MongoDB 命令)' },
  { name: 'drop', snippet: "drop: '${1:collection}'", detail: '(MongoDB 命令)' },
  { name: 'listCollections', snippet: 'listCollections: 1', detail: '(MongoDB 命令)' },
  { name: 'listIndexes', snippet: "listIndexes: '${1:collection}'", detail: '(MongoDB 命令)' },
];

const getCursorContext = (model: monaco.editor.ITextModel, position: monaco.Position): IMongodbCursorContext => {
  const textBeforeCursor = model.getValueInRange(new monaco.Range(1, 1, position.lineNumber, position.column));
  const lineBeforeCursor = model.getLineContent(position.lineNumber).slice(0, position.column - 1);
  const tokenMatch = lineBeforeCursor.match(/[\w$]*$/);
  const inputValue = tokenMatch?.[0] || '';

  return {
    inputValue,
    range: new monaco.Range(
      position.lineNumber,
      position.column - inputValue.length,
      position.lineNumber,
      position.column,
    ),
    textBeforeCursor,
  };
};

const matchesInput = (value: string, inputValue: string) => {
  return !inputValue || value.toLowerCase().startsWith(inputValue.toLowerCase());
};

const buildDefinitionSuggestion = (
  definition: IMongodbCompletionDefinition,
  range: monaco.Range,
  monacoIns: typeof monaco,
  sortPrefix = 'B',
): monaco.languages.CompletionItem => ({
  label: definition.name,
  insertText: definition.snippet || definition.name,
  insertTextRules: definition.snippet ? monacoIns.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
  sortText: `${sortPrefix}${definition.name}`,
  kind: monacoIns.languages.CompletionItemKind.Method,
  detail: definition.detail,
  documentation: definition.documentation,
  range,
});

const buildOperatorSuggestions = (
  operators: string[],
  inputValue: string,
  range: monaco.Range,
  monacoIns: typeof monaco,
  detail: string,
): monaco.languages.CompletionItem[] => {
  return operators
    .filter((operator) => matchesInput(operator, inputValue))
    .map((operator) => ({
      label: operator,
      insertText: `${operator}: `,
      sortText: `B${operator}`,
      kind: monacoIns.languages.CompletionItemKind.Operator,
      detail,
      range,
    }));
};

const getCollectionName = (textBeforeCursor: string): string => {
  const directMatches = Array.from(
    textBeforeCursor.matchAll(
      /db\.([A-Za-z_$][\w$]*)\.(?:find|findOne|aggregate|countDocuments|insertOne|insertMany|updateOne|updateMany|replaceOne|deleteOne|deleteMany|createIndex|dropIndex|drop)\s*\(/g,
    ),
  );
  const directName = directMatches[directMatches.length - 1]?.[1];
  if (directName) {
    return directName;
  }

  const getCollectionMatches = Array.from(
    textBeforeCursor.matchAll(/db\.getCollection\(\s*(['"])([^'"]+)\1\s*\)\.(?:\w+)\s*\(/g),
  );
  return getCollectionMatches[getCollectionMatches.length - 1]?.[2] || '';
};

const getCollectionSuggestions = async (
  boundInfo: IBoundInfo,
  inputValue: string,
  range: monaco.Range,
  monacoIns: typeof monaco,
): Promise<monaco.languages.CompletionItem[]> => {
  try {
    const data = await sqlService.getAllTableList({
      dataSourceId: boundInfo.dataSourceId,
      databaseName: boundInfo.databaseName,
      schemaName: boundInfo.schemaName,
    });

    return data
      .filter((collection) => matchesInput(collection.name, inputValue))
      .map((collection) => {
        const name = collection.name;
        const isIdentifier = /^[A-Za-z_$][\w$]*$/.test(name);
        return {
          label: name,
          insertText: isIdentifier ? name : `getCollection('${name.replace(/'/g, "\\'")}')`,
          sortText: `A${name}`,
          kind: monacoIns.languages.CompletionItemKind.Struct,
          detail: '(MongoDB 集合)',
          documentation: collection.comment || `集合: ${name}`,
          range,
        };
      });
  } catch (error) {
    console.error('[MongoDB 补全 - API] 获取集合失败:', error);
    return [];
  }
};

const getFieldSuggestions = async (
  boundInfo: IBoundInfo,
  collectionName: string,
  inputValue: string,
  range: monaco.Range,
  monacoIns: typeof monaco,
): Promise<monaco.languages.CompletionItem[]> => {
  const cacheKey = [boundInfo.dataSourceId, boundInfo.databaseName || '', collectionName].join('_');
  let columns = mongodbFieldCache.get(cacheKey);

  if (!columns) {
    try {
      columns = await sqlService.getColumnList({
        dataSourceId: boundInfo.dataSourceId,
        databaseName: boundInfo.databaseName || '',
        schemaName: boundInfo.schemaName,
        tableName: collectionName,
      });
      mongodbFieldCache.set(cacheKey, columns);
    } catch (error) {
      console.error('[MongoDB 补全 - API] 获取字段失败:', error);
      return [];
    }
  }

  return columns
    .filter((column) => matchesInput(column.name, inputValue))
    .map((column) => {
      const name = column.name;
      const dataType = column.columnType || column.dataType || '';
      return {
        label: name,
        insertText: `${/^[A-Za-z_$][\w$]*$/.test(name) ? name : `'${name.replace(/'/g, "\\'")}'`}: `,
        sortText: `A${name}`,
        kind: monacoIns.languages.CompletionItemKind.Field,
        detail: `(MongoDB 字段) ${dataType}`,
        documentation: column.comment || `字段: ${name}, 类型: ${dataType}`,
        range,
      };
    });
};

export const isMongodbDatabase = (databaseType?: string) => databaseType?.toUpperCase() === 'MONGODB';

export const initMongodbAutocomplete = (options: ISqlAutocompleteOptions): ISqlAutocompleteDisposable => {
  const { monaco: monacoIns, boundInfo } = options;
  const provider = monacoIns.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: '.${, abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_'.split(''),
    provideCompletionItems: async (model, position) => {
      const { inputValue, range, textBeforeCursor } = getCursorContext(model, position);
      const cursorMethodMatch = textBeforeCursor.match(/\.(?:find|findOne)\s*\([^;]*\)\.([\w$]*)$/s);

      if (cursorMethodMatch) {
        const methodInput = cursorMethodMatch[1] || '';
        return {
          suggestions: cursorMethods
            .filter((method) => matchesInput(method.name, methodInput))
            .map((method) => buildDefinitionSuggestion(method, range, monacoIns, 'A')),
        };
      }

      const collectionMethodMatch = textBeforeCursor.match(
        /(?:db\.[A-Za-z_$][\w$]*|db\.getCollection\(\s*(['"])[^'"]+\1\s*\))\.([\w$]*)$/,
      );

      if (collectionMethodMatch) {
        const methodInput = collectionMethodMatch[2] || '';
        return {
          suggestions: collectionMethods
            .filter((method) => matchesInput(method.name, methodInput))
            .map((method) => buildDefinitionSuggestion(method, range, monacoIns, 'A')),
        };
      }

      const dbMemberMatch = textBeforeCursor.match(/db\.([\w$]*)$/);
      if (dbMemberMatch) {
        const memberInput = dbMemberMatch[1] || '';
        const collections = await getCollectionSuggestions(boundInfo, memberInput, range, monacoIns);
        const methods = databaseMethods
          .filter((method) => matchesInput(method.name, memberInput))
          .map((method) => buildDefinitionSuggestion(method, range, monacoIns));
        return { suggestions: [...collections, ...methods] };
      }

      if (/db\.runCommand\(\s*\{[^}]*$/s.test(textBeforeCursor)) {
        return {
          suggestions: runCommandSnippets
            .filter((command) => matchesInput(command.name, inputValue))
            .map((command) => buildDefinitionSuggestion(command, range, monacoIns, 'A')),
        };
      }

      const collectionName = getCollectionName(textBeforeCursor);
      if (collectionName) {
        const fieldSuggestions = await getFieldSuggestions(boundInfo, collectionName, inputValue, range, monacoIns);
        const operators = [
          ...buildOperatorSuggestions(queryOperators, inputValue, range, monacoIns, '(MongoDB 查询操作符)'),
          ...buildOperatorSuggestions(updateOperators, inputValue, range, monacoIns, '(MongoDB 更新操作符)'),
        ];
        const stages = /\.aggregate\s*\(/.test(textBeforeCursor)
          ? buildOperatorSuggestions(aggregationStages, inputValue, range, monacoIns, '(MongoDB 聚合阶段)')
          : [];
        const helpers = bsonHelpers
          .filter((helper) => matchesInput(helper.name, inputValue))
          .map((helper) => buildDefinitionSuggestion(helper, range, monacoIns, 'C'));
        return { suggestions: [...fieldSuggestions, ...stages, ...operators, ...helpers] };
      }

      return {
        suggestions: rootDefinitions
          .filter((helper) => matchesInput(helper.name, inputValue))
          .map((helper) => buildDefinitionSuggestion(helper, range, monacoIns)),
      };
    },
  });

  return {
    dispose: () => provider.dispose(),
  };
};

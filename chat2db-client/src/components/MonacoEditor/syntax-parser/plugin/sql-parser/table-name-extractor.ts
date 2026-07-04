import { mysqlParser } from './mysql';

const unquoteIdentifier = (identifier: string): string => {
  const trimmed = identifier.trim();
  if (
    (trimmed.startsWith('`') && trimmed.endsWith('`')) ||
    (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
    (trimmed.startsWith('[') && trimmed.endsWith(']'))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
};

const normalizeTableName = (tableName: string): string => {
  const parts = tableName
    .split('.')
    .map(unquoteIdentifier)
    .filter(Boolean);
  return parts[parts.length - 1] || '';
};

const addTableName = (tableNames: Map<string, string>, rawTableName?: string, cteNames?: Set<string>) => {
  const tableName = normalizeTableName(rawTableName || '');
  const normalizedName = tableName.toLowerCase();
  if (!tableName || cteNames?.has(normalizedName) || tableNames.has(normalizedName)) {
    return;
  }
  tableNames.set(normalizedName, tableName);
};

const collectAstTableNames = (node: any, tableNames: Map<string, string>, cteNames: Set<string>) => {
  if (!node || typeof node !== 'object') {
    return;
  }

  if (node.variant === 'table' && node.name?.tableName?.value) {
    addTableName(tableNames, node.name.tableName.value, cteNames);
  }

  if (Array.isArray(node)) {
    node.forEach((item) => collectAstTableNames(item, tableNames, cteNames));
    return;
  }

  Object.values(node).forEach((value) => collectAstTableNames(value, tableNames, cteNames));
};

const stripCommentsAndStrings = (sql: string): string => {
  let result = '';
  let index = 0;
  while (index < sql.length) {
    const current = sql[index];
    const next = sql[index + 1];

    if (current === '-' && next === '-') {
      result += '  ';
      index += 2;
      while (index < sql.length && sql[index] !== '\n' && sql[index] !== '\r') {
        result += ' ';
        index += 1;
      }
      continue;
    }

    if (current === '/' && next === '*') {
      result += '  ';
      index += 2;
      while (index < sql.length && !(sql[index] === '*' && sql[index + 1] === '/')) {
        result += /\s/.test(sql[index]) ? sql[index] : ' ';
        index += 1;
      }
      if (index < sql.length) {
        result += '  ';
        index += 2;
      }
      continue;
    }

    if (current === "'") {
      result += ' ';
      index += 1;
      while (index < sql.length) {
        if (sql[index] === "'" && sql[index + 1] === "'") {
          result += '  ';
          index += 2;
          continue;
        }
        if (sql[index] === "'") {
          result += ' ';
          index += 1;
          break;
        }
        result += /\s/.test(sql[index]) ? sql[index] : ' ';
        index += 1;
      }
      continue;
    }

    result += current;
    index += 1;
  }
  return result;
};

const tokenizeSql = (sql: string): string[] =>
  stripCommentsAndStrings(sql).match(/`[^`]*`|"[^"]*"|\[[^\]]*\]|[A-Za-z_$][\w$]*|[().,;]/g) || [];

const collectCteNames = (tokens: string[]): Set<string> => {
  const cteNames = new Set<string>();
  if (tokens[0]?.toLowerCase() !== 'with') {
    return cteNames;
  }

  for (let index = 1; index < tokens.length - 1; index += 1) {
    const previous = tokens[index - 1]?.toLowerCase();
    const next = tokens[index + 1]?.toLowerCase();
    if ((index === 1 || previous === ',') && next === 'as') {
      cteNames.add(normalizeTableName(tokens[index]).toLowerCase());
    }
  }
  return cteNames;
};

const CLAUSE_KEYWORDS = new Set([
  'where',
  'group',
  'order',
  'having',
  'limit',
  'union',
  'except',
  'intersect',
  'qualify',
  'window',
  'returning',
  'set',
  'values',
]);

const collectLexicalTableNames = (sql: string, tableNames: Map<string, string>) => {
  const tokens = tokenizeSql(sql);
  const cteNames = collectCteNames(tokens);
  const fromDepths = new Set<number>();
  let depth = 0;
  let expectTable = false;

  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    const lowerToken = token.toLowerCase();

    if (token === '(') {
      depth += 1;
      expectTable = false;
      continue;
    }
    if (token === ')') {
      fromDepths.delete(depth);
      depth = Math.max(0, depth - 1);
      expectTable = false;
      continue;
    }

    if (lowerToken === 'from') {
      fromDepths.add(depth);
      expectTable = true;
      continue;
    }
    if (lowerToken === 'join') {
      expectTable = true;
      continue;
    }
    if (CLAUSE_KEYWORDS.has(lowerToken)) {
      fromDepths.delete(depth);
      expectTable = false;
      continue;
    }
    if (token === ',' && fromDepths.has(depth)) {
      expectTable = true;
      continue;
    }
    if (!expectTable || token === ';') {
      continue;
    }

    let qualifiedName = token;
    while (tokens[index + 1] === '.' && tokens[index + 2]) {
      qualifiedName += `.${tokens[index + 2]}`;
      index += 2;
    }
    addTableName(tableNames, qualifiedName, cteNames);
    expectTable = false;
  }
};

export const extractTableNames = (sql: string): string[] => {
  const tableNames = new Map<string, string>();
  if (!sql.trim()) {
    return [];
  }
  const cteNames = collectCteNames(tokenizeSql(sql));

  try {
    const parseResult = mysqlParser(sql, sql.length);
    collectAstTableNames(parseResult?.ast, tableNames, cteNames);
    if (!parseResult?.success) {
      collectLexicalTableNames(sql, tableNames);
    }
  } catch {
    collectLexicalTableNames(sql, tableNames);
  }

  return [...tableNames.values()];
};

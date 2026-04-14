/* eslint-disable no-case-declarations */
import * as _ from 'lodash';
import { IToken } from '../../..';
import {
  ICompletionItem,
  ICursorInfo,
  IGetFieldsByTableName,
  ISelectStatement,
  ISource,
  IStatement,
  IStatements,
} from './define';

export async function getCursorInfo(rootStatement: IStatements, keyPath: string[]) {
  // console.log('[Reader] getCursorInfo - keyPath:', keyPath);
  
  if (!rootStatement) {
    // console.log('[Reader] getCursorInfo - rootStatement 为空');
    return null;
  }

  const cursorValue: IToken = _.get(rootStatement, keyPath);
  const cursorKey = keyPath.slice().pop();
  const parentStatement = _.get(rootStatement, keyPath.slice(0, keyPath.length - 1));

  // console.log('[Reader] getCursorInfo - cursorValue:', cursorValue);
  // console.log('[Reader] getCursorInfo - cursorKey:', cursorKey);
  // console.log('[Reader] getCursorInfo - parentStatement:', parentStatement);

  if (!parentStatement) {
    // console.log('[Reader] getCursorInfo - parentStatement 为空');
    return null;
  }

  const result = await judgeStatement(parentStatement, async typePlusVariant => {
    // console.log('[Reader] getCursorInfo - typePlusVariant:', typePlusVariant);
    
    switch (typePlusVariant) {
      case 'identifier.tableName':
        // console.log('[Reader] getCursorInfo - 识别为表名');
        return {
          type: 'tableName',
          variant: cursorKey,
          token: cursorValue,
          tableInfo: parentStatement,
        };
      case 'identifier.column':
        if (cursorKey === 'name') {
          // console.log('[Reader] getCursorInfo - 识别为普通字段');
          return {
            type: 'tableField',
            token: cursorValue,
          };
        }
        return null;

      case 'identifier.columnAfterGroup':
        // console.log('[Reader] getCursorInfo - 识别为表名限定字段，groupName:', parentStatement.groupName.value);
        return {
          type: 'tableFieldAfterGroup',
          token: cursorValue,
          groupName: parentStatement.groupName.value,
        };
      case 'function':
        // console.log('[Reader] getCursorInfo - 识别为函数');
        return {
          type: 'functionName',
          token: cursorValue,
        };
      default:
        // console.log('[Reader] getCursorInfo - 未知类型:', typePlusVariant);
    }
  });
  
  // console.log('[Reader] getCursorInfo - 最终结果:', result);
  return result as ICursorInfo;
}

export function findNearestStatement(
  rootStatement: IStatements,
  keyPath: string[],
  callback?: (value?: any) => boolean,
): ISelectStatement {
  if (!rootStatement) {
    return null;
  }

  if (keyPath.length === 0) {
    return null;
  }

  const value = _.get(rootStatement, keyPath);

  if (!value) {
    throw Error('Path not found from ast!');
  }

  if (!value.token && value.type === 'statement') {
    if (callback) {
      if (callback(value) === true) {
        return value;
      }
    } else {
      return value;
    }
  }

  if (keyPath.length > 1) {
    return findNearestStatement(rootStatement, keyPath.slice(0, keyPath.length - 1), callback);
  }
  return null;
}

export async function getFieldsFromStatement(
  rootStatement: IStatements,
  cursorKeyPath: string[],
  getFieldsByTableName: IGetFieldsByTableName,
) {
  // console.log('[Reader] getFieldsFromStatement - 开始执行');
  
  const cursorInfo = await getCursorInfo(rootStatement, cursorKeyPath);
  const cursorRootStatement = findNearestStatement(rootStatement, cursorKeyPath);

  // console.log('[Reader] getFieldsFromStatement - cursorRootStatement:', cursorRootStatement?.variant);

  if (!cursorRootStatement) {
    // console.log('[Reader] getFieldsFromStatement - cursorRootStatement 为空，返回空数组');
    return [];
  }

  switch (cursorRootStatement.variant) {
    // Select statement
    case 'select':
      // console.log('[Reader] getFieldsFromStatement - SELECT 语句模式');
      return getFieldsByFromClauses(
        cursorRootStatement,
        _.get(cursorRootStatement, 'from.sources', []),
        cursorInfo,
        getFieldsByTableName,
      );
    // Join statement
    // 字段是 source 表的（自带 + join 的表）
    case 'join':
      // console.log('[Reader] getFieldsFromStatement - JOIN 语句模式');
      const parentCursorKeyPath = cursorKeyPath.slice();
      parentCursorKeyPath.pop();

      const parentSelectStatement = findNearestStatement(rootStatement, parentCursorKeyPath, eachStatement => {
        return eachStatement.variant === 'select';
      });

      // console.log('[Reader] getFieldsFromStatement - 父级 SELECT:', parentSelectStatement?.variant);

      return getFieldsByFromClauses(
        parentSelectStatement,
        _.get(parentSelectStatement, 'from.sources', []),
        cursorInfo,
        getFieldsByTableName,
      );
    default:
      // console.log('[Reader] getFieldsFromStatement - 未知 variant:', cursorRootStatement.variant);
  }

  return [];
}

async function getFieldsByFromClauses(
  rootStatement: IStatement,
  fromStatements: IStatement[],
  cursorInfo: ICursorInfo,
  getFieldsByTableName: IGetFieldsByTableName,
): Promise<ICompletionItem[]> {
  const fields = await Promise.all(
    fromStatements.map(fromStatement => {
      return getFieldsByFromClause(rootStatement, fromStatement, cursorInfo, getFieldsByTableName);
    }),
  );

  return _.flatten(fields).filter(item => {
    return !!item;
  });
}

async function getFieldsByFromClause(
  rootStatement: IStatement,
  fromStatement: IStatement,
  cursorInfo: ICursorInfo,
  getFieldsByTableName: IGetFieldsByTableName,
): Promise<ICompletionItem[]> {
  return judgeStatement(fromStatement, async typePlusVariant => {
    // console.log('[Reader] getFieldsByFromClause - typePlusVariant:', typePlusVariant);
    
    switch (typePlusVariant) {
      case 'statement.tableSource':
        // ignore joins
        const tableSourceFields = await getFieldsByFromClause(
          rootStatement,
          (fromStatement as any).source,
          cursorInfo,
          getFieldsByTableName,
        );
        const joinsFields = _.isArray((fromStatement as any).joins)
          ? await getFieldsByFromClauses(
              rootStatement,
              _.get(fromStatement, 'joins', []),
              cursorInfo,
              getFieldsByTableName,
            )
          : [];
        // console.log('[Reader] getFieldsByFromClause - tableSource 字段数:', tableSourceFields.length, 'join 字段数:', joinsFields.length);
        return tableSourceFields.concat(joinsFields);
      case 'statement.join':
        return getFieldsByFromClause(rootStatement, (fromStatement as any).join, cursorInfo, getFieldsByTableName);
      case 'identifier.table':
        const itFromStatement = fromStatement as ISource;

        // console.log('[Reader] getFieldsByFromClause - 处理表标识符，表信息:', itFromStatement.name);
        // console.log('[Reader] getFieldsByFromClause - 别名:', itFromStatement.alias);

        let originFields = await getFieldsByTableName(itFromStatement.name, cursorInfo.token.value, rootStatement);
        const tableNames: string[] = _.get(itFromStatement, 'name.tableNames', []);

        // console.log('[Reader] getFieldsByFromClause - 原始字段数:', originFields.length);

        let groupPickerName: string = null;
        const tableNameAlias: string = _.get(itFromStatement, 'alias.value');

        // 如果有 alias，直接作为 groupPickerName
        if (tableNameAlias) {
          // console.log('[Reader] getFieldsByFromClause - 使用别名作为分组名:', tableNameAlias);
          groupPickerName = tableNameAlias;
        } else {
          // 实现的 tableNames 数量
          let existKeyCount = 0;
          tableNames.forEach(tableName => {
            const eachTableName = _.get(itFromStatement, `name.${tableName}.value`);
            if (eachTableName) {
              // eslint-disable-next-line no-plusplus
              existKeyCount++;
              groupPickerName = eachTableName;
            }
          });

          // 如果 existKeyCount 大于 1，则不提供 groupPickerName
          if (existKeyCount > 1) {
            // console.log('[Reader] getFieldsByFromClause - 多个表名有值，不提供分组名');
            groupPickerName = null;
          } else {
            // console.log('[Reader] getFieldsByFromClause - 使用表名作为分组名:', groupPickerName);
          }
        }

        originFields = originFields.map(originField => {
          return {
            ...originField,
            tableInfo: itFromStatement.name,
            // 如果仅有一个 tableNames 有值，就用那个作为 groupPickerName，否则没有
            groupPickerName,
            // existKeyCount
            //     ? null
            //     : _.get(itFromStatement, 'alias.value') || _.get(itFromStatement, 'name.tableName.value') || null,
            originFieldName: originField.label,
          };
        });
        
        // console.log('[Reader] getFieldsByFromClause - 处理后的字段示例:', originFields[0]);
        return originFields;
      case 'statement.select':
        const ssFromStatement = fromStatement as ISelectStatement;

        // console.log('[Reader] getFieldsByFromClause - 处理子查询');

        let statementSelectFields: ICompletionItem[] = [];

        const fields = await getFieldsByFromClauses(
          ssFromStatement,
          ssFromStatement.from.sources,
          cursorInfo,
          getFieldsByTableName,
        );

        // console.log('[Reader] getFieldsByFromClause - 子查询字段数:', fields.length);

        // If select *, return all fields
        if (ssFromStatement.result.length === 1 && ssFromStatement.result[0].name.value === '*') {
          // console.log('[Reader] getFieldsByFromClause - SELECT * 模式，返回所有字段');
          statementSelectFields = fields.slice();
        } else {
          // console.log('[Reader] getFieldsByFromClause - SELECT 指定字段模式');
          statementSelectFields = fields
            .map(field => {
              const selectedField = ssFromStatement.result.find(result => {
                if (_.get(result.name, 'token') === true) {
                  return result.name.value === field.label;
                }

                // Consider ${group}.${field}
                if (
                  _.get(result.name, 'type') === 'identifier' &&
                  _.get(result.name, 'variant') === 'columnAfterGroup'
                ) {
                  return _.get(result.name, 'name.value') === field.label;
                }

                // Consider ${group}.*
                if (_.get(result.name, 'type') === 'identifier' && _.get(result.name, 'variant') === 'groupAll') {
                  return _.get(result.name, 'groupName.value') === field.groupPickerName;
                }

                return false;
              });
              if (!selectedField) {
                return null;
              }

              if (selectedField.alias) {
                return {
                  ...field,
                  label: selectedField.alias.value,
                };
              }
              return field;
            })
            .filter(field => {
              return field !== null;
            })
            .slice();
        }

        // console.log('[Reader] getFieldsByFromClause - 筛选后字段数:', statementSelectFields.length);

        // If has alias, change
        if (_.has(ssFromStatement, 'alias.value')) {
          // console.log('[Reader] getFieldsByFromClause - 子查询有别名:', _.get(ssFromStatement, 'alias.value'));
          statementSelectFields = statementSelectFields.map(statementSelectField => {
            return {
              ...statementSelectField,
              groupPickerName: _.get(ssFromStatement, 'alias.value'),
            };
          });
        }

        return statementSelectFields;
      default:
        // console.log('[Reader] getFieldsByFromClause - 未知类型:', typePlusVariant);
        return null;
    }
  });
}

async function judgeStatement<T>(
  statement: IStatement,
  callback: (typePlusVariant?: string) => Promise<T>,
): Promise<T> {
  if (!statement) {
    return null;
  }

  if (statement.variant) {
    return callback(`${statement.type}.${statement.variant}`);
  }
  return callback(statement.type);
}

export async function findFieldExtraInfo(
  rootStatement: IStatements,
  cursorInfo: ICursorInfo,
  getFieldsByTableName: IGetFieldsByTableName,
  fieldKeyPath: string[],
): Promise<ICompletionItem> {
  const fields = await getFieldsFromStatement(rootStatement, fieldKeyPath, getFieldsByTableName);
  const field = fields.find(eachField => {
    return eachField.label === cursorInfo.token.value;
  });

  if (!field) {
    return null;
  }

  return field;
}

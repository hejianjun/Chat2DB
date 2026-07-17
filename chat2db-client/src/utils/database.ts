import { DatabaseTypeCode } from '@/constants/common';
import { Option } from '@/typings/common';

export function handleDatabaseAndSchema(databaseAndSchema: any) {
  let newCascaderOptions: Option[] = [];
  if (databaseAndSchema.databases) {
    newCascaderOptions = (databaseAndSchema?.databases || []).map((t) => {
      let schemasList: Option[] = [];
      if (t.schemas) {
        schemasList = t.schemas.map((t) => {
          return {
            value: t.name,
            label: t.name,
            type: 'schema',
            isLeaf: true,
          };
        });
      }
      return {
        value: t.name,
        label: t.name,
        type: 'database',
        children: schemasList,
        isLeaf: schemasList.length === 0,
      };
    });
  } else if (databaseAndSchema?.schemas) {
    newCascaderOptions = (databaseAndSchema?.schemas || []).map((t) => {
      return {
        value: t.name,
        label: t.name,
        type: 'schema',
        isLeaf: true,
      };
    });
  }
  return newCascaderOptions;
}

/**
 * 兼容处理数据库名称
 * @param databaseName
 * @param databaseType
 * @returns
 */
export function compatibleDataBaseName(databaseName: string, databaseType: DatabaseTypeCode, schemaName: string = '') {
  //""  oracele  sqlite postgrsql  h2 dm
  // ` MYSQL clickhouse MariaDB
  // [ sqlserver
  if (
    [
      DatabaseTypeCode.ORACLE,
      DatabaseTypeCode.SQLITE,
      DatabaseTypeCode.POSTGRESQL,
      DatabaseTypeCode.H2,
      DatabaseTypeCode.DB2,
      DatabaseTypeCode.KINGBASE,
      DatabaseTypeCode.DM,
    ].includes(databaseType)
  ) {
    return `"${databaseName}"`;
  } else if ([DatabaseTypeCode.SQLSERVER].includes(databaseType)) {
    return `[${databaseName}]`;
  } else if ([DatabaseTypeCode.MYSQL, DatabaseTypeCode.CLICKHOUSE, DatabaseTypeCode.MARIADB].includes(databaseType)) {
    return `\`${databaseName}\``;
  } else if ([DatabaseTypeCode.PHOENIX].includes(databaseType)) {
    return `${schemaName}.${databaseName}`;
  } else {
    return `${databaseName}`;
  }
}

const escapeMongodbCollectionName = (collectionName: string) => {
  return collectionName.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
};

const normalizeMongodbObject = (value?: string) => {
  const trimmedValue = value?.trim();
  if (!trimmedValue) {
    return '{}';
  }
  if (trimmedValue.startsWith('{') && trimmedValue.endsWith('}')) {
    return trimmedValue;
  }
  return `{ ${trimmedValue} }`;
};

export const getMongodbCollectionReference = (collectionName: string) => {
  if (/^[A-Za-z_$][\w$]*$/.test(collectionName)) {
    return `db.${collectionName}`;
  }
  return `db.getCollection('${escapeMongodbCollectionName(collectionName)}')`;
};

export const buildMongodbFindQuery = (collectionName: string, filter?: string, sort?: string) => {
  const collectionReference = getMongodbCollectionReference(collectionName);
  const findQuery = `${collectionReference}.find(${normalizeMongodbObject(filter)})`;
  return sort?.trim() ? `${findQuery}.sort(${normalizeMongodbObject(sort)})` : findQuery;
};

import MonacoEditor, { IExportRefFunction } from '@/components/MonacoEditor';
import { DatabaseTypeCode } from '@/constants';
import sqlServer from '@/service/sql';
import { openModal } from '@/store/common/components';
import { IIndex, IIndexColumn, ITreeNode } from '@/typings';
import { compatibleDataBaseName, getMongodbCollectionReference } from '@/utils/database';
import React, { memo, useEffect, useRef, useState } from 'react';
import { v4 as uuid } from 'uuid';

import styles from './viewDDL.less';

const MYSQL_FAMILY = [DatabaseTypeCode.MYSQL, DatabaseTypeCode.MARIADB];

const getColumnDirection = (column: IIndexColumn) => {
  const direction = column.ascOrDesc || column.collation;
  if (/^(A|ASC)$/i.test(direction || '')) {
    return ' ASC';
  }
  if (/^(D|DESC)$/i.test(direction || '')) {
    return ' DESC';
  }
  return '';
};

const getOrderedColumns = (indexData: IIndex) =>
  [...(indexData.columnList || [])]
    .sort((left, right) => (left.ordinalPosition || 0) - (right.ordinalPosition || 0))
    .filter((column) => column.columnName);

const buildMySqlIndexDefinition = (treeNodeData: ITreeNode, indexData: IIndex) => {
  const databaseType = treeNodeData.extraParams!.databaseType;
  const quote = (name: string) => compatibleDataBaseName(name, databaseType);
  const tableName = treeNodeData.extraParams?.tableName || '';
  const columns = getOrderedColumns(indexData)
    .map((column) => `${quote(column.columnName!)}${getColumnDirection(column)}`)
    .join(', ');
  const type = indexData.type?.toLowerCase();

  if (type === 'primary' || indexData.name.toLowerCase() === 'primary') {
    return `ALTER TABLE ${quote(tableName)} ADD PRIMARY KEY (${columns});`;
  }

  const keyword =
    type === 'unique' ? 'UNIQUE ' : type === 'fulltext' ? 'FULLTEXT ' : type === 'spatial' ? 'SPATIAL ' : '';
  const comment = indexData.comment ? ` COMMENT '${indexData.comment.replace(/'/g, "''")}'` : '';
  return `CREATE ${keyword}INDEX ${quote(indexData.name)} ON ${quote(tableName)} (${columns})${comment};`;
};

const buildMetadataDefinition = (treeNodeData: ITreeNode, indexData: IIndex) => {
  const columns =
    getOrderedColumns(indexData)
      .map((column) => `${column.columnName}${getColumnDirection(column)}`)
      .join(', ') ||
    indexData.columns ||
    '-';

  return [
    `Index: ${indexData.name}`,
    `Table: ${treeNodeData.extraParams?.tableName || '-'}`,
    `Type: ${indexData.type || '-'}`,
    `Columns: ${columns}`,
    `Comment: ${indexData.comment || '-'}`,
  ].join('\n');
};

const getMongoIndexKeyValue = (column: IIndexColumn) => {
  const type = column.ascOrDesc || column.collation;
  if (/^(A|ASC)$/i.test(type || '')) {
    return '1';
  }
  if (/^(D|DESC)$/i.test(type || '')) {
    return '-1';
  }
  return JSON.stringify(type || 1);
};

const buildMongoIndexDefinition = (treeNodeData: ITreeNode, indexData: IIndex) => {
  const collectionName = treeNodeData.extraParams?.tableName || '';
  const keys = getOrderedColumns(indexData)
    .map((column) => `${JSON.stringify(column.columnName)}: ${getMongoIndexKeyValue(column)}`)
    .join(', ');
  const unique = indexData.unique || indexData.type?.toLowerCase() === 'unique';
  const options = [`name: ${JSON.stringify(indexData.name)}`, ...(unique ? ['unique: true'] : [])].join(', ');

  return `${getMongodbCollectionReference(collectionName)}.createIndex(\n  { ${keys} },\n  { ${options} }\n);`;
};

export const buildIndexDefinition = (treeNodeData: ITreeNode) => {
  const indexData = treeNodeData.indexData;
  if (!indexData) {
    return '';
  }
  if (MYSQL_FAMILY.includes(treeNodeData.extraParams!.databaseType)) {
    return buildMySqlIndexDefinition(treeNodeData, indexData);
  }
  if (treeNodeData.extraParams!.databaseType === DatabaseTypeCode.MONGODB) {
    return buildMongoIndexDefinition(treeNodeData, indexData);
  }
  return buildMetadataDefinition(treeNodeData, indexData);
};

export const extractMySqlIndexDefinition = (tableDDL: string, indexName: string) => {
  const quotedIndexName = `\`${indexName.replace(/`/g, '``')}\``;
  const definition = tableDDL
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) =>
      indexName.toLowerCase() === 'primary'
        ? /^PRIMARY KEY\b/i.test(line)
        : /\bKEY\b/i.test(line) && line.includes(quotedIndexName),
    );

  return definition?.replace(/,\s*$/, '');
};

const loadIndexDefinition = async (treeNodeData: ITreeNode) => {
  if (!MYSQL_FAMILY.includes(treeNodeData.extraParams!.databaseType)) {
    return buildIndexDefinition(treeNodeData);
  }

  const tableName = treeNodeData.extraParams?.tableName || '';
  try {
    const tableDDL = await sqlServer.exportCreateTableSql({
      dataSourceId: treeNodeData.extraParams!.dataSourceId,
      databaseName: treeNodeData.extraParams!.databaseName!,
      schemaName: treeNodeData.extraParams?.schemaName,
      tableName,
    });
    const definition = extractMySqlIndexDefinition(tableDDL, treeNodeData.name);
    if (definition) {
      const quotedTableName = compatibleDataBaseName(tableName, treeNodeData.extraParams!.databaseType);
      return `ALTER TABLE ${quotedTableName} ADD ${definition};`;
    }
  } catch {
    // 表 DDL 获取失败时仍展示已加载的索引元数据，避免弹窗为空。
  }
  return buildIndexDefinition(treeNodeData);
};

const IndexDefinitionEditor = memo<{ treeNodeData: ITreeNode }>(({ treeNodeData }) => {
  const [editorId] = useState(uuid());
  const editorRef = useRef<IExportRefFunction>(null);

  useEffect(() => {
    loadIndexDefinition(treeNodeData).then((definition) => {
      editorRef.current?.setValue(definition, 'reset');
    });
  }, [treeNodeData]);

  return (
    <MonacoEditor
      id={editorId}
      ref={editorRef}
      options={{
        readOnly: true,
        glyphMargin: false,
        folding: false,
      }}
    />
  );
});

export const viewIndexDefinition = (treeNodeData: ITreeNode) => {
  openModal({
    title: `${treeNodeData.name} - ${treeNodeData.extraParams?.tableName || ''}`,
    width: '60%',
    footer: false,
    content: (
      <div className={styles.monacoEditorBox}>
        <IndexDefinitionEditor treeNodeData={treeNodeData} />
      </div>
    ),
  });
};

import { TableDataType } from '@/constants';
import i18n from '@/i18n';
import sqlServer, { IColumn } from '@/service/sql';
import { openModal } from '@/store/common/components';
import { IIndex, IIndexColumn, IManageResultData, ITreeNode } from '@/typings';
import { Alert, Button, Descriptions, Spin, Table, Tabs, Tag } from 'antd';
import React, { memo, useCallback, useEffect, useState } from 'react';

import styles from './viewCollectionInfo.less';

type ICollectionStats = Record<string, string | null>;

type ICollectionInfo = {
  stats: ICollectionStats;
  statsCommand: string;
  columnList: IColumn[];
  indexList: IIndex[];
};

type ICollectionIndex = IIndex;

const numberFormatter = new Intl.NumberFormat();

const formatNumber = (value?: string | number | null) => {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const number = Number(value);
  return Number.isFinite(number) ? numberFormatter.format(number) : String(value);
};

const formatBytes = (value?: string | number | null) => {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  const bytes = Number(value);
  if (!Number.isFinite(bytes)) {
    return String(value);
  }
  if (bytes === 0) {
    return '0 B';
  }
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB'];
  const unitIndex = Math.min(Math.floor(Math.log(Math.abs(bytes)) / Math.log(1024)), units.length - 1);
  const displayValue = bytes / 1024 ** unitIndex;
  const humanReadable = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(displayValue);
  return `${humanReadable} ${units[unitIndex]} (${numberFormatter.format(bytes)} B)`;
};

const formatIndexKeyType = (value?: string | null) => {
  if (/^(A|ASC)$/i.test(value || '')) {
    return '1';
  }
  if (/^(D|DESC)$/i.test(value || '')) {
    return '-1';
  }
  return value || '1';
};

const getOrderedIndexColumns = (record: ICollectionIndex) =>
  [...(record.columnList || [])].sort((left, right) => (left.ordinalPosition || 0) - (right.ordinalPosition || 0));

const buildStats = (result: IManageResultData): ICollectionStats => {
  if (!result?.success) {
    throw new Error(result?.message || i18n('workspace.mongodb.collectionInfo.loadError'));
  }
  const row = result.dataList?.[0];
  if (!row) {
    throw new Error(i18n('workspace.mongodb.collectionInfo.emptyStats'));
  }
  return result.headerList.reduce<ICollectionStats>((stats, header, index) => {
    if (header.dataType !== TableDataType.CHAT2DB_ROW_NUMBER) {
      stats[header.name] = row[index] ?? null;
    }
    return stats;
  }, {});
};

const parseJsonObject = (value?: string | null): Record<string, unknown> => {
  if (!value) {
    return {};
  }
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

const prettyJsonText = (value: string) => {
  let output = '';
  let indent = 0;
  let inString = false;
  let escaped = false;
  const indentation = () => '  '.repeat(indent);

  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];
    if (inString) {
      output += character;
      if (escaped) {
        escaped = false;
      } else if (character === '\\') {
        escaped = true;
      } else if (character === '"') {
        inString = false;
      }
      continue;
    }
    if (character === '"') {
      inString = true;
      output += character;
    } else if (character === '{' || character === '[') {
      output += character;
      indent += 1;
      output += `\n${indentation()}`;
    } else if (character === '}' || character === ']') {
      indent = Math.max(0, indent - 1);
      output += `\n${indentation()}${character}`;
    } else if (character === ',') {
      output += `,\n${indentation()}`;
    } else if (character === ':') {
      output += ': ';
    } else if (!/\s/.test(character)) {
      output += character;
    }
  }
  return output;
};

const formatDetail = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const trimmed = value.trim();
  if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    return prettyJsonText(trimmed);
  }
  return value;
};

const formatRawStats = (stats: ICollectionStats) => {
  const entries = Object.entries(stats);
  const lines = entries.map(([name, value]) => {
    if (value === null) {
      return `  ${JSON.stringify(name)}: null`;
    }
    const trimmed = value.trim();
    let formattedValue: string;
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
      formattedValue = prettyJsonText(trimmed);
    } else if (/^(true|false|null|-?\d+(\.\d+)?)$/.test(trimmed)) {
      formattedValue = trimmed;
    } else {
      formattedValue = JSON.stringify(value);
    }
    return `  ${JSON.stringify(name)}: ${formattedValue.replace(/\n/g, '\n  ')}`;
  });
  return `{\n${lines.join(',\n')}\n}`;
};

const booleanTag = (value?: string | null) => {
  const enabled = value === 'true' || value === '1';
  return <Tag color={enabled ? 'green' : undefined}>{i18n(enabled ? 'common.text.is' : 'common.text.no')}</Tag>;
};

const CollectionInfo = memo<{ treeNodeData: ITreeNode }>(({ treeNodeData }) => {
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [collectionInfo, setCollectionInfo] = useState<ICollectionInfo | null>(null);

  const collectionName = treeNodeData.extraParams?.tableName || treeNodeData.name;
  const databaseName = treeNodeData.extraParams?.databaseName;
  const statsCommand = `db.getCollection(${JSON.stringify(collectionName)}).stats()`;

  const loadCollectionInfo = useCallback(async () => {
    const dataSourceId = treeNodeData.extraParams?.dataSourceId;
    if (dataSourceId === undefined || !databaseName || !collectionName) {
      setLoadError(i18n('workspace.mongodb.collectionInfo.loadError'));
      setLoading(false);
      return;
    }

    setLoading(true);
    setLoadError(null);
    try {
      const requestParams = {
        dataSourceId,
        databaseName,
        schemaName: treeNodeData.extraParams?.schemaName,
        tableName: collectionName,
      };
      const [statsResults, columnList, indexList] = await Promise.all([
        sqlServer.executeSql({
          sql: statsCommand,
          dataSourceId,
          databaseName,
          schemaName: treeNodeData.extraParams?.schemaName,
          pageNo: 1,
          pageSize: 1,
        }),
        sqlServer.getColumnList(requestParams),
        sqlServer.getIndexList(requestParams),
      ]);
      setCollectionInfo({
        stats: buildStats(statsResults[0]),
        statsCommand,
        columnList,
        indexList,
      });
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : i18n('workspace.mongodb.collectionInfo.loadError'));
    } finally {
      setLoading(false);
    }
  }, [collectionName, databaseName, statsCommand, treeNodeData.extraParams]);

  useEffect(() => {
    loadCollectionInfo();
  }, [loadCollectionInfo]);

  const stats = collectionInfo?.stats || {};
  const indexSizes = parseJsonObject(stats.indexSizes);
  const fieldList = collectionInfo?.columnList || [];
  const indexList = collectionInfo?.indexList || [];

  const fieldColumns = [
    {
      title: i18n('workspace.mongodb.collectionInfo.fieldName'),
      dataIndex: 'name',
      key: 'name',
      width: 220,
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.bsonType'),
      dataIndex: 'columnType',
      key: 'columnType',
      width: 180,
      render: (value: string | null) => value || '-',
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.logicalType'),
      dataIndex: 'dataType',
      key: 'dataType',
      width: 180,
      render: (value: string | null) => value || '-',
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.primaryKey'),
      dataIndex: 'primaryKey',
      key: 'primaryKey',
      width: 120,
      render: (value: boolean | null) => (
        <Tag color={value ? 'green' : undefined}>{i18n(value ? 'common.text.is' : 'common.text.no')}</Tag>
      ),
    },
  ];

  const indexColumns = [
    {
      title: i18n('workspace.mongodb.collectionInfo.indexName'),
      dataIndex: 'name',
      key: 'name',
      width: 200,
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.indexType'),
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (value: string | null) => value || '-',
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.unique'),
      key: 'unique',
      width: 100,
      render: (_: unknown, record: ICollectionIndex) => {
        const unique = record.unique || record.type?.toLowerCase() === 'unique';
        return <Tag color={unique ? 'green' : undefined}>{i18n(unique ? 'common.text.is' : 'common.text.no')}</Tag>;
      },
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.indexSize'),
      key: 'indexSize',
      width: 190,
      render: (_: unknown, record: ICollectionIndex) => formatBytes(indexSizes[record.name] as string | number | null),
    },
    {
      title: i18n('workspace.mongodb.collectionInfo.indexKeys'),
      key: 'columnList',
      render: (_: unknown, record: ICollectionIndex) => (
        <div className={styles.indexKeys}>
          {getOrderedIndexColumns(record).map((column: IIndexColumn) => (
            <code key={`${column.columnName}-${column.ordinalPosition}`}>
              {column.columnName}: {formatIndexKeyType(column.ascOrDesc || column.collation)}
            </code>
          ))}
        </div>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      <div className={styles.collectionInfo}>
        {loadError ? (
          <Alert
            type="error"
            showIcon
            message={i18n('workspace.mongodb.collectionInfo.loadError')}
            description={loadError}
            action={
              <Button size="small" onClick={loadCollectionInfo}>
                {i18n('common.button.refresh')}
              </Button>
            }
          />
        ) : collectionInfo ? (
          <>
            <Alert
              className={styles.hint}
              type="info"
              showIcon
              message={
                <span>
                  {i18n('workspace.mongodb.collectionInfo.statsCommand')}
                  <code>{collectionInfo.statsCommand}</code>
                </span>
              }
              description={i18n('workspace.mongodb.collectionInfo.sampleHint')}
            />
            <Descriptions className={styles.summary} bordered size="small" column={4}>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.database')}>
                {databaseName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.collection')}>
                {collectionName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.namespace')}>
                {stats.ns || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.documents')}>
                {formatNumber(stats.count)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.dataSize')}>
                {formatBytes(stats.size)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.averageObjectSize')}>
                {formatBytes(stats.avgObjSize)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.storageSize')}>
                {formatBytes(stats.storageSize)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.totalIndexSize')}>
                {formatBytes(stats.totalIndexSize)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.indexCount')}>
                {formatNumber(stats.nindexes)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.capped')}>
                {booleanTag(stats.capped)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.sharded')}>
                {booleanTag(stats.sharded)}
              </Descriptions.Item>
              <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.primaryShard')}>
                {stats.primary || '-'}
              </Descriptions.Item>
            </Descriptions>
            <Tabs
              items={[
                {
                  key: 'fields',
                  label: `${i18n('workspace.mongodb.collectionInfo.fields')} (${fieldList.length})`,
                  children: (
                    <Table<IColumn>
                      rowKey="name"
                      size="small"
                      pagination={false}
                      columns={fieldColumns}
                      dataSource={fieldList}
                      locale={{ emptyText: i18n('common.text.noData') }}
                      scroll={{ y: 330 }}
                    />
                  ),
                },
                {
                  key: 'indexes',
                  label: `${i18n('workspace.mongodb.collectionInfo.indexes')} (${indexList.length})`,
                  children: (
                    <div className={styles.tabContent}>
                      <Table<ICollectionIndex>
                        rowKey="name"
                        size="small"
                        pagination={false}
                        columns={indexColumns}
                        dataSource={indexList}
                        locale={{ emptyText: i18n('common.text.noData') }}
                        scroll={{ y: 260 }}
                      />
                      <div className={styles.detailTitle}>
                        {i18n('workspace.mongodb.collectionInfo.indexEngineDetails')}
                      </div>
                      <pre className={styles.jsonDetail}>{formatDetail(stats.indexDetails)}</pre>
                    </div>
                  ),
                },
                {
                  key: 'sharding',
                  label: i18n('workspace.mongodb.collectionInfo.sharding'),
                  children: (
                    <div className={styles.tabContent}>
                      <Descriptions bordered size="small" column={3}>
                        <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.sharded')}>
                          {booleanTag(stats.sharded)}
                        </Descriptions.Item>
                        <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.primaryShard')}>
                          {stats.primary || '-'}
                        </Descriptions.Item>
                        <Descriptions.Item label={i18n('workspace.mongodb.collectionInfo.chunkCount')}>
                          {formatNumber(stats.nchunks)}
                        </Descriptions.Item>
                      </Descriptions>
                      <div className={styles.detailTitle}>{i18n('workspace.mongodb.collectionInfo.shardDetails')}</div>
                      <pre className={styles.jsonDetail}>{formatDetail(stats.shards)}</pre>
                    </div>
                  ),
                },
                {
                  key: 'storage',
                  label: i18n('workspace.mongodb.collectionInfo.storageEngine'),
                  children: (
                    <div className={styles.tabContent}>
                      <div className={styles.detailTitle}>
                        {i18n('workspace.mongodb.collectionInfo.storageEngineDetails')}
                      </div>
                      <pre className={styles.jsonDetail}>{formatDetail(stats.wiredTiger)}</pre>
                    </div>
                  ),
                },
                {
                  key: 'raw',
                  label: i18n('workspace.mongodb.collectionInfo.rawStats'),
                  children: <pre className={styles.rawStats}>{formatRawStats(stats)}</pre>,
                },
              ]}
            />
          </>
        ) : null}
      </div>
    </Spin>
  );
});

export const viewCollectionInfo = (treeNodeData: ITreeNode) => {
  openModal({
    title: `${treeNodeData.name} - ${i18n('workspace.menu.viewCollectionInfo')}`,
    width: '78%',
    footer: false,
    content: <CollectionInfo treeNodeData={treeNodeData} />,
  });
};

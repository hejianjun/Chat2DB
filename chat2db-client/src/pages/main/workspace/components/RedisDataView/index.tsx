import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Empty, Input, InputNumber, Modal, Segmented, Select, Table, Tag, Tooltip, message } from 'antd';
import {
  BulbOutlined,
  CloseOutlined,
  CodeOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import type { ColumnsType, SorterResult, TablePaginationConfig } from 'antd/es/table/interface';
import { v4 as uuid } from 'uuid';

import redisService, { IRedisKeyItem, IRedisKeyPrefixBuildStatus, IRedisKeyPrefixItem } from '@/service/redis';
import {
  IRedisKeyPrefixCommentResult,
  setCurrentWorkspaceExtend,
  setPendingAiChat,
} from '@/pages/main/workspace/store/common';

import styles from './index.less';

interface IProps {
  uniqueData: {
    dataSourceId: number;
    databaseName?: string;
  };
}

interface IRedisKeyRow extends IRedisKeyItem {
  rowKey: string;
  displayName: string;
  isGroup?: boolean;
  count?: number;
  comment?: string;
  inheritedComment?: string;
  keyCount?: number;
  sampleTypes?: string[];
  prefixOnly?: boolean;
  children?: IRedisKeyRow[];
}

const DEFAULT_LIMIT = 1000;
const NO_TTL = -1;
const KEY_TYPES = ['string', 'hash', 'list', 'set', 'zset'];
const SEARCH_MODE_KEY = 'key';
const SEARCH_MODE_PREFIX = 'prefix';
const RUNNING_STATUS = 'RUNNING';
const DONE_STATUS = 'DONE';
const PREFIX_STATUS_POLL_INTERVAL = 3000;
const DEFAULT_PREFIX_SORT = { field: 'keyCount', order: 'descend' as const };
const REDIS_PREFIX_DELIMITERS = ':_-.';

interface IHashFieldRow {
  id: string;
  field: string;
  value: string;
}

const RedisDataView = memo((props: IProps) => {
  const { uniqueData } = props;
  const [searchKey, setSearchKey] = useState('');
  const [searchMode, setSearchMode] = useState<string>(SEARCH_MODE_KEY);
  const [data, setData] = useState<IRedisKeyItem[]>([]);
  const [prefixComments, setPrefixComments] = useState<Record<string, IRedisKeyPrefixItem>>({});
  const [prefixSearchData, setPrefixSearchData] = useState<IRedisKeyPrefixItem[]>([]);
  const [prefixSamples, setPrefixSamples] = useState<IRedisKeyItem[]>([]);
  const [prefixBuildStatus, setPrefixBuildStatus] = useState<IRedisKeyPrefixBuildStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [prefixSaving, setPrefixSaving] = useState(false);
  const [prefixGuessing, setPrefixGuessing] = useState(false);
  const [prefixLoading, setPrefixLoading] = useState(false);
  const [prefixSampleLoading, setPrefixSampleLoading] = useState(false);
  const [selectedKey, setSelectedKey] = useState('');
  const [selectedPrefix, setSelectedPrefix] = useState('');
  const [prefixCommentDraft, setPrefixCommentDraft] = useState('');
  const [detail, setDetail] = useState<IRedisKeyItem | null>(null);
  const [editorKey, setEditorKey] = useState('');
  const [editorType, setEditorType] = useState('string');
  const [editorTtl, setEditorTtl] = useState<number | null>(NO_TTL);
  const [textValue, setTextValue] = useState('');
  const [isJsonString, setIsJsonString] = useState(false);
  const [hashFields, setHashFields] = useState<IHashFieldRow[]>([]);
  const [originalHashFields, setOriginalHashFields] = useState<Record<string, string>>({});
  const [hashFieldFilter, setHashFieldFilter] = useState('');
  const [lastRefreshTime, setLastRefreshTime] = useState<string>('');
  const [keyLimit, setKeyLimit] = useState(DEFAULT_LIMIT);
  const [scanCursor, setScanCursor] = useState('0');
  const [hasMore, setHasMore] = useState(false);
  const [creating, setCreating] = useState(false);
  const closeStreamRef = useRef<(() => void) | null>(null);
  const streamIdRef = useRef('');
  const prefixRefreshRequestRef = useRef('');
  const detailRequestRef = useRef('');
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const [tableScrollY, setTableScrollY] = useState(240);
  const [tableScrollX, setTableScrollX] = useState(1000);
  // 排序状态：field 为排序字段，order 为排序方向（ascend/descend），null 表示取消排序
  const [sortInfo, setSortInfo] = useState<{ field: string; order: 'ascend' | 'descend' } | null>(null);

  const hydrateEditor = (keyDetail: IRedisKeyItem) => {
    const type = normalizeType(keyDetail.type);
    const value = keyDetail.value;
    const ttl = keyDetail.ttl === undefined || keyDetail.ttl === null ? NO_TTL : keyDetail.ttl;
    setEditorKey(keyDetail.name);
    setEditorType(type || 'string');
    setEditorTtl(ttl);
    setHashFieldFilter('');
    setIsJsonString(false);
    if (type === 'hash' && value && typeof value === 'object' && !Array.isArray(value)) {
      const originalMap = Object.fromEntries(Object.entries(value).map(([k, v]) => [k, String(v ?? '')]));
      setOriginalHashFields(originalMap);
      setHashFields(
        Object.entries(value).map(([field, fieldValue]) => ({
          id: uuid(),
          field,
          value: String(fieldValue ?? ''),
        })),
      );
      setTextValue('');
      return;
    }
    setOriginalHashFields({});
    setHashFields([]);
    if (Array.isArray(value)) {
      setTextValue(value.map((item) => String(item ?? '')).join('\n'));
      return;
    }
    const nextTextValue = value === undefined || value === null ? '' : String(value);
    if (type === 'string') {
      const formattedJson = formatJson(nextTextValue);
      if (formattedJson !== null) {
        setTextValue(formattedJson);
        setIsJsonString(true);
        return;
      }
    }
    setTextValue(nextTextValue);
  };

  const loadPrefixBuildStatus = async () => {
    if (!uniqueData?.dataSourceId) {
      return null;
    }
    try {
      const status = await redisService.getKeyPrefixBuildStatus({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
      })
      setPrefixBuildStatus(status || null);
      return status || null;
    } catch {
      setPrefixBuildStatus(null);
      return null;
    }
  };

  const loadPrefixComments = (keyword = '', refresh = false, syncSearchResult = false) => {
    if (!uniqueData?.dataSourceId) {
      return Promise.resolve();
    }
    setPrefixLoading(true);
    return redisService
      .searchKeyPrefixes({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        searchKey: keyword,
        refresh,
        sortField: 'name',
        sortOrder: 'ascend',
      })
      .then((items) => {
        const list = items || [];
        setPrefixComments((current) => mergePrefixComments(current, list));
        if (syncSearchResult) {
          setPrefixSearchData(list);
        }
      })
      .catch((error) => {
        if (syncSearchResult) {
          message.error(String(error || 'Redis key 前缀加载失败'));
        }
      })
      .finally(() => setPrefixLoading(false));
  };

  const searchPrefixes = (keyword = searchKey, refresh = false) => {
    const searchRequest = loadPrefixComments(keyword, refresh, true);
    loadPrefixBuildStatus();
    return searchRequest;
  };

  const waitPrefixBuildDone = async (requestId: string) => {
    while (prefixRefreshRequestRef.current === requestId) {
      const status = await loadPrefixBuildStatus();
      if (prefixRefreshRequestRef.current !== requestId || status?.status !== RUNNING_STATUS) {
        return status;
      }
      await delay(PREFIX_STATUS_POLL_INTERVAL);
    }
    return null;
  };

  const refreshPrefixCache = async () => {
    if (!uniqueData?.dataSourceId) {
      return;
    }
    const requestId = uuid();
    prefixRefreshRequestRef.current = requestId;
    setPrefixLoading(true);
    try {
      await redisService.refreshKeyPrefixCache({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
      })
      message.success('Redis key 前缀缓存已开始刷新');
      const status = await waitPrefixBuildDone(requestId);
      if (prefixRefreshRequestRef.current !== requestId) {
        return;
      }
      if (status?.status === DONE_STATUS) {
        await searchPrefixes(searchMode === SEARCH_MODE_PREFIX ? searchKey : '', false);
        return;
      }
      message.error(status?.errorMessage || 'Redis key 前缀缓存刷新失败');
    } catch (error) {
      message.error(String(error || 'Redis key 前缀缓存刷新失败'));
    } finally {
      if (prefixRefreshRequestRef.current === requestId) {
        setPrefixLoading(false);
      }
    }
  };

  const loadPrefixSamples = (prefix: string) => {
    if (!uniqueData?.dataSourceId || !prefix) {
      return;
    }
    setPrefixSampleLoading(true);
    redisService
      .sampleKeyPrefixTypes({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        prefix,
      })
      .then((items) => setPrefixSamples(items || []))
      .catch((error) => {
        setPrefixSamples([]);
        message.error(String(error || 'Redis key 前缀样例加载失败'));
      })
      .finally(() => setPrefixSampleLoading(false));
  };

  const openPrefixEditor = (record: IRedisKeyRow) => {
    setCreating(false);
    setDetail(null);
    setSelectedKey('');
    setSelectedPrefix(record.name);
    setPrefixCommentDraft(record.comment || prefixComments[record.name]?.comment || '');
    setPrefixSamples([]);
    loadPrefixSamples(record.name);
  };

  const savePrefixComment = () => {
    if (!uniqueData?.dataSourceId || !selectedPrefix) {
      return;
    }
    const comment = prefixCommentDraft.trim();
    setPrefixSaving(true);
    redisService
      .updateKeyPrefixComment({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        prefix: selectedPrefix,
        comment,
      })
      .then(() => {
        setPrefixComments((current) => ({
          ...current,
          [selectedPrefix]: {
            ...(current[selectedPrefix] || { name: selectedPrefix }),
            name: selectedPrefix,
            comment,
          },
        }));
        setPrefixSearchData((current) =>
          current.map((item) => (item.name === selectedPrefix ? { ...item, comment } : item)),
        );
        message.success('Redis key 前缀注释已保存');
      })
      .catch((error) => {
        message.error(String(error || 'Redis key 前缀注释保存失败'));
      })
      .finally(() => setPrefixSaving(false));
  };

  const saveGeneratedPrefixComments = useCallback(
    async (result: IRedisKeyPrefixCommentResult) => {
      const generated = (result.prefixes || []).filter((item) => item.prefix?.trim() && item.comment?.trim());
      if (!generated.length || !uniqueData?.dataSourceId) {
        message.warning('AI 未返回可保存的 Redis key 注释');
        setPrefixGuessing(false);
        return;
      }

      const nextComments = Object.fromEntries(
        generated.map((item) => [item.prefix.trim(), item.comment.trim()]),
      ) as Record<string, string>;

      try {
        await Promise.all(
          Object.entries(nextComments).map(([prefix, comment]) =>
            redisService.updateKeyPrefixComment({
              dataSourceId: uniqueData.dataSourceId,
              databaseName: uniqueData.databaseName,
              prefix,
              comment,
            }),
          ),
        );

        setPrefixComments((current) => {
          const next = { ...current };
          Object.entries(nextComments).forEach(([prefix, comment]) => {
            next[prefix] = {
              ...(next[prefix] || { name: prefix }),
              name: prefix,
              comment,
            };
          });
          return next;
        });
        setPrefixSearchData((current) =>
          current.map((item) => (nextComments[item.name] ? { ...item, comment: nextComments[item.name] } : item)),
        );
        if (selectedPrefix && nextComments[selectedPrefix]) {
          setPrefixCommentDraft(nextComments[selectedPrefix]);
        }
        message.success(`已保存 ${Object.keys(nextComments).length} 条 Redis key 注释`);
      } catch (error) {
        message.error(String(error || 'Redis key 注释保存失败'));
      } finally {
        setPrefixGuessing(false);
      }
    },
    [selectedPrefix, uniqueData?.dataSourceId, uniqueData?.databaseName],
  );

  const loadKeysByPrefix = () => {
    if (!selectedPrefix) {
      return;
    }
    openLoadKeysByPrefixModal(selectedPrefix);
  };

  const openLoadKeysByPrefixModal = (prefixName: string) => {
    if (!prefixName) {
      return;
    }
    let suffix = '';
    Modal.confirm({
      title: '加载 Keys',
      className: styles.loadKeysModal,
      icon: null,
      width: 600,
      content: (
        <Input
          addonBefore={prefixName}
          autoFocus
          placeholder="请输入 Key 后缀，留空则匹配全部"
          onChange={(event) => {
            suffix = event.target.value;
          }}
        />
      ),
      okText: '加载',
      cancelText: '取消',
      onOk: () => loadKeysByPrefixName(prefixName, suffix),
    });
  };

  const loadKeysByPrefixName = (prefixName: string, suffix = '') => {
    const normalizedSuffix = suffix.trim();
    const keyword = `${prefixName}${normalizedSuffix || '*'}`;
    setSearchMode(SEARCH_MODE_KEY);
    setSearchKey(keyword);
    loadData(keyword, keyLimit);
  };

  const loadData = (keyword = searchKey, count = keyLimit, cursor = '0', append = false) => {
    if (!uniqueData?.dataSourceId) {
      return;
    }
    closeStreamRef.current?.();
    const streamId = uuid();
    streamIdRef.current = streamId;
    if (!append) {
      setData([]);
      setScanCursor('0');
      setHasMore(false);
    }
    setLoading(true);
    closeStreamRef.current = redisService.streamKeyList({
      uid: streamId,
      dataSourceId: uniqueData.dataSourceId,
      databaseName: uniqueData.databaseName,
      searchKey: keyword,
      cursor,
      count,
      onBatch: (items) => {
        if (streamIdRef.current !== streamId) {
          return;
        }
        setData((current) => current.concat(items || []));
      },
      onDone: (_, nextCursor, nextHasMore) => {
        if (streamIdRef.current !== streamId) {
          return;
        }
        setScanCursor(nextCursor);
        setHasMore(nextHasMore);
        setLoading(false);
        setLastRefreshTime(new Date().toLocaleTimeString());
        closeStreamRef.current = null;
      },
      onError: (errorMessage) => {
        if (streamIdRef.current !== streamId) {
          return;
        }
        message.error(errorMessage || 'Redis key 加载失败');
        setLoading(false);
        closeStreamRef.current = null;
      },
    });
  };

  const loadKeyDetail = (keyName: string) => {
    if (!uniqueData?.dataSourceId || !keyName) {
      return;
    }
    const requestId = uuid();
    detailRequestRef.current = requestId;
    setSelectedKey(keyName);
    setSelectedPrefix('');
    setCreating(false);
    setDetail(null);
    setDetailLoading(true);
    redisService
      .queryKey({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        keyName,
      })
      .then((keyDetail) => {
        if (detailRequestRef.current !== requestId) {
          return;
        }
        setDetail(keyDetail);
        hydrateEditor(keyDetail);
      })
      .catch((error) => {
        message.error(String(error || 'Redis key 详情加载失败'));
      })
      .finally(() => {
        if (detailRequestRef.current === requestId) {
          setDetailLoading(false);
        }
      });
  };

  const saveKey = () => {
    if (!uniqueData?.dataSourceId || (!detail && !creating)) {
      return;
    }
    const nextKey = editorKey.trim();
    if (!nextKey) {
      message.warning('键名不能为空');
      return;
    }
    const type = normalizeType(creating ? editorType : detail?.type);
    if (type === 'string' && isJsonString && formatJson(textValue) === null) {
      message.error('JSON 格式无效，请修正后再保存');
      return;
    }
    setSaving(true);

    let saveRequest: Promise<any>;

    if (creating) {
      const value = buildEditorValue(type, textValue, hashFields);
      saveRequest = redisService.createKey({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        name: nextKey,
        keyType: type,
        value,
        ttl: editorTtl ?? NO_TTL,
      });
    } else if (type === 'hash' && nextKey === detail!.name) {
      const { addedFields, removedFields } = computeHashDiff(originalHashFields, hashFields);
      const hasFieldChanges = Object.keys(addedFields).length > 0 || removedFields.length > 0;
      const hasTtlChange = editorTtl !== (detail!.ttl ?? NO_TTL);
      if (hasFieldChanges || hasTtlChange) {
        saveRequest = redisService.partialUpdateKey({
          dataSourceId: uniqueData.dataSourceId,
          databaseName: uniqueData.databaseName,
          keyName: nextKey,
          keyType: type,
          addedFields,
          removedFields,
          updateTtl: editorTtl ?? NO_TTL,
        });
      } else {
        message.info('没有修改内容');
        setSaving(false);
        return;
      }
    } else {
      const value = buildEditorValue(type, textValue, hashFields);
      saveRequest = redisService.updateKey({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        originalKey: detail!.name,
        updateKey: nextKey,
        keyType: type,
        value,
        updateTtl: editorTtl ?? NO_TTL,
      });
    }

    saveRequest
      .then(() => {
        message.success(creating ? 'Redis key 已新增' : 'Redis key 已保存');
        setCreating(false);
        setSelectedKey(nextKey);
        loadKeyDetail(nextKey);
        loadData(searchKey, keyLimit);
      })
      .catch((error) => {
        message.error(String(error || 'Redis key 保存失败'));
      })
      .finally(() => setSaving(false));
  };

  const addHashField = () => {
    setHashFields((current) => current.concat({ id: uuid(), field: '', value: '' }));
  };

  const updateHashField = (id: string, patch: Partial<IHashFieldRow>) => {
    setHashFields((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  };

  const removeHashField = (id: string) => {
    setHashFields((current) => current.filter((item) => item.id !== id));
  };

  const openCreateKey = () => {
    detailRequestRef.current = '';
    setCreating(true);
    setSelectedKey('');
    setSelectedPrefix('');
    setDetail({ name: '', type: 'string', value: '', ttl: NO_TTL });
    setEditorKey('');
    setEditorType('string');
    setEditorTtl(NO_TTL);
    setTextValue('');
    setIsJsonString(false);
    setHashFields([{ id: uuid(), field: '', value: '' }]);
    setOriginalHashFields({});
    setHashFieldFilter('');
  };

  const deleteSelectedKey = () => {
    const keyName = detail?.name || selectedKey;
    if (!uniqueData?.dataSourceId || !keyName || creating) {
      message.warning('请选择要删除的 key');
      return;
    }
    Modal.confirm({
      title: '删除 Redis key',
      content: keyName,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () =>
        redisService
          .deleteKey({
            dataSourceId: uniqueData.dataSourceId,
            databaseName: uniqueData.databaseName,
            keyName,
          })
          .then(() => {
            message.success('Redis key 已删除');
            setSelectedKey('');
            setSelectedPrefix('');
            setDetail(null);
            loadData(searchKey, keyLimit);
          })
          .catch((error) => {
            message.error(String(error || 'Redis key 删除失败'));
          }),
    });
  };

  const refreshData = () => {
    if (searchMode === SEARCH_MODE_PREFIX) {
      searchPrefixes(searchKey);
      return;
    }
    loadData(searchKey, keyLimit);
  };

  const getMoreData = () => {
    if (!hasMore) {
      message.info('没有更多 Redis key');
      return;
    }
    setKeyLimit(DEFAULT_LIMIT);
    loadData(searchKey, DEFAULT_LIMIT, scanCursor, true);
  };

  const getAllData = () => {
    setKeyLimit(-1);
    loadData(searchKey, -1);
  };

  useEffect(() => {
    let cancelled = false;
    const initializeData = async () => {
      const status = await loadPrefixBuildStatus();
      if (cancelled) {
        return;
      }
      setSearchKey('');
      if (status?.status === DONE_STATUS) {
        closeStreamRef.current?.();
        setData([]);
        setScanCursor('0');
        setHasMore(false);
        setSearchMode(SEARCH_MODE_PREFIX);
        setSortInfo(DEFAULT_PREFIX_SORT);
        await loadPrefixComments('', false, true);
        return;
      }
      setSearchMode(SEARCH_MODE_KEY);
      setSortInfo(null);
      loadData('');
      loadPrefixComments('');
    };
    initializeData();
    return () => {
      cancelled = true;
      closeStreamRef.current?.();
      closeStreamRef.current = null;
    };
  }, [uniqueData?.dataSourceId, uniqueData?.databaseName]);

  useEffect(() => {
    if (prefixBuildStatus?.status !== RUNNING_STATUS || !uniqueData?.dataSourceId) {
      return;
    }
    const timer = window.setInterval(() => {
      loadPrefixBuildStatus();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [prefixBuildStatus?.status, searchMode, searchKey, uniqueData?.dataSourceId, uniqueData?.databaseName]);

  useEffect(() => {
    const tableWrap = tableWrapRef.current;
    if (!tableWrap) {
      return;
    }

    const updateTableScrollY = () => {
      const headerHeight = tableWrap.querySelector('.ant-table-thead')?.getBoundingClientRect().height || 39;
      const { height: wrapHeight, width: wrapWidth } = tableWrap.getBoundingClientRect();
      setTableScrollY(Math.max(120, Math.floor(wrapHeight - headerHeight)));
      setTableScrollX(Math.max(1000, Math.floor(wrapWidth)));
    };

    updateTableScrollY();

    if (!window.ResizeObserver) {
      window.addEventListener('resize', updateTableScrollY);
      return () => window.removeEventListener('resize', updateTableScrollY);
    }

    const resizeObserver = new ResizeObserver(updateTableScrollY);
    resizeObserver.observe(tableWrap);
    return () => resizeObserver.disconnect();
  }, []);

  // 构建树形数据，若有排序则递归排序每层 children
  const tableData = useMemo(() => {
    const tree =
      searchMode === SEARCH_MODE_PREFIX ? buildPrefixRows(prefixSearchData) : buildRows(data, prefixComments);
    if (!sortInfo) {
      return tree;
    }
    return sortTree(tree, sortInfo.field, sortInfo.order);
  }, [data, prefixComments, prefixSearchData, searchMode, sortInfo]);

  const openAiChatForPrefixComments = useCallback(
    (onlySelected = false) => {
      if (!uniqueData?.dataSourceId) {
        message.warning('请先选择 Redis 连接');
        return;
      }

      const targetPrefixes = onlySelected
        ? flattenPrefixRows(tableData).filter((item) => item.name === selectedPrefix)
        : flattenPrefixRows(tableData).filter((item) => !item.comment?.trim());

      if (!targetPrefixes.length) {
        message.warning(onlySelected ? '请先选择 Redis key 前缀' : '当前可见前缀都已有注释');
        return;
      }

      const prefixes = targetPrefixes.slice(0, 30).map((item) => ({
        prefix: item.name,
        keyCount: item.keyCount || item.count || 0,
        sampleKeys: collectSampleKeys(item).slice(0, 5),
      }));

      setPrefixGuessing(true);
      setPendingAiChat({
        dataSourceId: Number(uniqueData.dataSourceId),
        databaseName: uniqueData.databaseName,
        message: '请根据 Redis key 前缀和样例 key 生成合适的中文注释',
        promptType: 'REDIS_KEYS_COMMENT',
        ext: JSON.stringify({ prefixes }),
        onRedisKeyPrefixCommentGenerated: saveGeneratedPrefixComments,
      });
      setCurrentWorkspaceExtend('ai');
      setPrefixGuessing(false);
      message.success('已切换到 AI 助手，请在 AI 聊天面板中查看推荐结果');
    },
    [saveGeneratedPrefixComments, selectedPrefix, tableData, uniqueData?.dataSourceId, uniqueData?.databaseName],
  );

  const filteredHashFields = useMemo(() => {
    const keyword = hashFieldFilter.trim().toLowerCase();
    if (!keyword) {
      return hashFields;
    }
    return hashFields.filter(
      (item) => item.field.toLowerCase().includes(keyword) || item.value.toLowerCase().includes(keyword),
    );
  }, [hashFieldFilter, hashFields]);

  const hashColumns: ColumnsType<IHashFieldRow> = [
    {
      title: '字段',
      dataIndex: 'field',
      width: 220,
      render: (value, record) => (
        <Input value={value} onChange={(event) => updateHashField(record.id, { field: event.target.value })} />
      ),
    },
    {
      title: '值',
      dataIndex: 'value',
      render: (value, record) => (
        <Input value={value} onChange={(event) => updateHashField(record.id, { value: event.target.value })} />
      ),
    },
    {
      title: '',
      dataIndex: 'action',
      width: 44,
      render: (_, record) => (
        <Button icon={<DeleteOutlined />} size="small" type="text" onClick={() => removeHashField(record.id)} />
      ),
    },
  ];

  const columns: ColumnsType<IRedisKeyRow> = useMemo(
    () => [
      {
        title: '键',
        dataIndex: 'displayName',
        width: '28%',
        sorter: true,
        sortOrder: sortInfo?.field === 'displayName' ? sortInfo.order : undefined,
        render: (text, record) => {
          if (record.isGroup) {
            return (
              <span className={styles.groupName}>
                {text}
                {searchMode !== SEARCH_MODE_PREFIX && <span> ({record.count})</span>}
              </span>
            );
          }
          return (
            <Tooltip title={record.name}>
              <div className={styles.keyName}>{text}</div>
            </Tooltip>
          );
        },
      },
      ...(searchMode === SEARCH_MODE_PREFIX
        ? [
            {
              title: 'Key 数量',
              dataIndex: 'keyCount',
              width: 120,
              sorter: true,
              sortOrder: sortInfo?.field === 'keyCount' ? sortInfo.order : undefined,
              render: (keyCount?: number) => keyCount ?? 0,
            },
          ]
        : []),
      {
        title: '注释',
        dataIndex: 'comment',
        width: '24%',
        render: (comment, record) => {
          if (record.isGroup) {
            return comment ? (
              <Tooltip title={comment}>
                <div className={styles.commentCell}>{comment}</div>
              </Tooltip>
            ) : (
              <span className={styles.emptyComment}>-</span>
            );
          }
          if (!record.inheritedComment) {
            return null;
          }
          return (
            <Tooltip title={`继承自前缀注释: ${record.inheritedComment}`}>
              <div className={styles.commentCell}>{record.inheritedComment}</div>
            </Tooltip>
          );
        },
      },
      {
        title: '类型',
        dataIndex: 'type',
        width: searchMode === SEARCH_MODE_PREFIX ? 180 : 120,
        sorter: true,
        render: (type, record) => {
          if (record.prefixOnly) {
            const types = record.sampleTypes;
            return types?.length ? (
              <div className={styles.prefixTypeTags}>
                {types.map((sampleType) => (
                  <Tag color={getTypeColor(sampleType)} key={sampleType}>
                    {sampleType}
                  </Tag>
                ))}
              </div>
            ) : null;
          }
          if (record.isGroup || !type) {
            return null;
          }
          return <Tag color={getTypeColor(type)}>{type}</Tag>;
        },
      },
      {
        title: '值',
        dataIndex: 'value',
        render: (value, record) => {
          if (record.isGroup) {
            return null;
          }
          return (
            <Tooltip title={String(value || '')}>
              <div className={styles.valueCell}>{String(value || '')}</div>
            </Tooltip>
          );
        },
      },
      {
        title: '大小',
        dataIndex: 'size',
        width: 120,
        sorter: true,
        render: (size, record) => (record.isGroup ? null : formatSize(size)),
      },
      {
        title: 'TTL',
        dataIndex: 'ttl',
        width: 120,
        sorter: true,
        render: (ttl, record) => (record.isGroup ? null : formatTtl(ttl)),
      },
    ],
    [searchMode, sortInfo],
  );

  // 表格排序变化回调，三次点击循环：升序 → 降序 → 取消
  const handleTableChange = useCallback(
    (
      _pagination: TablePaginationConfig,
      _filters: Record<string, unknown>,
      sorter: SorterResult<IRedisKeyRow> | SorterResult<IRedisKeyRow>[],
    ) => {
      const s = Array.isArray(sorter) ? sorter[0] : sorter;
      if (s?.field && s.order) {
        setSortInfo((prev) => {
          if (prev?.field === s.field && prev?.order === s.order) {
            return null;
          }
          return { field: s.field as string, order: s.order as 'ascend' | 'descend' };
        });
      } else {
        setSortInfo(null);
      }
    },
    [],
  );

  return (
    <div className={styles.redisDataView}>
      <div className={styles.toolbar}>
        <Segmented
          size="small"
          value={searchMode}
          options={[
            { label: 'Key 搜索', value: SEARCH_MODE_KEY },
            { label: '前缀搜索', value: SEARCH_MODE_PREFIX },
          ]}
          onChange={(value) => {
            const nextMode = String(value);
            setSearchMode(nextMode);
            setSortInfo(nextMode === SEARCH_MODE_PREFIX ? DEFAULT_PREFIX_SORT : null);
            if (nextMode === SEARCH_MODE_PREFIX) {
              searchPrefixes(searchKey);
            }
          }}
        />
        <Input.Search
          className={styles.searchInput}
          allowClear
          prefix={<SearchOutlined />}
          placeholder={searchMode === SEARCH_MODE_PREFIX ? '前缀或注释' : '键包含'}
          value={searchKey}
          onChange={(event) => setSearchKey(event.target.value)}
          onSearch={(value) => {
            if (searchMode === SEARCH_MODE_PREFIX) {
              searchPrefixes(value);
              return;
            }
            setKeyLimit(DEFAULT_LIMIT);
            loadData(value, DEFAULT_LIMIT);
          }}
        />
        <div className={styles.meta}>
          {searchMode === SEARCH_MODE_PREFIX
            ? `${prefixSearchData.length} prefixes`
            : `${data.length} keys${lastRefreshTime ? ` · 上次刷新 ${lastRefreshTime}` : ''}`}
        </div>
      </div>
      <div className={styles.tableWrap} ref={tableWrapRef}>
        <Table
          bordered={false}
          columns={columns}
          dataSource={tableData}
          loading={searchMode === SEARCH_MODE_PREFIX ? prefixLoading : loading}
          pagination={false}
          rowClassName={(record) =>
            (record.name === selectedKey && !record.isGroup) || record.name === selectedPrefix ? styles.selectedRow : ''
          }
          rowKey="rowKey"
          size="small"
          scroll={{ x: tableScrollX, y: tableScrollY }}
          onChange={handleTableChange}
          virtual
          onRow={(record) => ({
            onClick: () => {
              if (record.isGroup) {
                openPrefixEditor(record);
                return;
              }
              if (!record.prefixOnly) {
                loadKeyDetail(record.name);
              }
            },
            onDoubleClick: () => {
              if (record.prefixOnly) {
                openPrefixEditor(record);
                openLoadKeysByPrefixModal(record.name);
              }
            },
          })}
        />
      </div>
      <div className={styles.bottomBar}>
        <div className={styles.bottomActions}>
          <Tooltip title="添加 key">
            <Button size="small" type="text" onClick={openCreateKey}>
              +
            </Button>
          </Tooltip>
          <Tooltip title="删除 key">
            <Button disabled={!selectedKey || creating} size="small" type="text" onClick={deleteSelectedKey}>
              -
            </Button>
          </Tooltip>
        </div>
        <div className={styles.bottomMeta}>
          <span>上次刷新时间: {lastRefreshTime || '-'}</span>
          <Button icon={<ReloadOutlined />} loading={loading} size="small" type="text" onClick={refreshData}>
            刷新
          </Button>
          <Button loading={prefixLoading} size="small" type="text" onClick={refreshPrefixCache}>
            前缀缓存
          </Button>
          <Button
            icon={<BulbOutlined />}
            loading={prefixGuessing}
            size="small"
            type="text"
            onClick={() => openAiChatForPrefixComments(false)}
          >
            AI 猜注释
          </Button>
          <span>{formatPrefixBuildStatus(prefixBuildStatus)}</span>
          <Button
            disabled={searchMode === SEARCH_MODE_PREFIX || !hasMore}
            loading={loading}
            size="small"
            type="text"
            onClick={getMoreData}
          >
            获取更多
          </Button>
          <Button
            disabled={searchMode === SEARCH_MODE_PREFIX}
            loading={loading}
            size="small"
            type="text"
            onClick={getAllData}
          >
            获取全部
          </Button>
          <Button icon={<SettingOutlined />} size="small" type="text" />
        </div>
      </div>
      <div className={styles.editorPane}>
        {selectedPrefix ? (
          <>
            <div className={styles.editorHeader}>
              <span>前缀注释</span>
              <div className={styles.editorActions}>
                <Button
                  icon={<CloseOutlined />}
                  size="small"
                  onClick={() => {
                    setSelectedPrefix('');
                    setPrefixCommentDraft('');
                    setPrefixSamples([]);
                  }}
                />
                <Button icon={<SearchOutlined />} size="small" onClick={loadKeysByPrefix}>
                  加载 keys
                </Button>
                <Button
                  icon={<BulbOutlined />}
                  loading={prefixGuessing}
                  size="small"
                  onClick={() => openAiChatForPrefixComments(true)}
                >
                  AI 猜测
                </Button>
                <Button
                  icon={<SaveOutlined />}
                  loading={prefixSaving}
                  size="small"
                  type="primary"
                  onClick={savePrefixComment}
                >
                  保存
                </Button>
              </div>
            </div>
            <div className={styles.editorBody}>
              <div className={styles.formRow}>
                <span className={styles.label}>前缀:</span>
                <Input value={selectedPrefix} readOnly />
              </div>
              <div className={styles.formRow}>
                <span className={styles.label}>数量:</span>
                <span className={styles.prefixCount}>{getPrefixCount(selectedPrefix, prefixComments, tableData)}</span>
              </div>
              <div className={styles.formRow}>
                <span className={styles.label}>注释:</span>
                <Input.TextArea
                  className={styles.prefixCommentInput}
                  value={prefixCommentDraft}
                  rows={3}
                  maxLength={500}
                  showCount
                  onChange={(event) => setPrefixCommentDraft(event.target.value)}
                />
              </div>
              <div className={styles.prefixSamples}>
                <div className={styles.prefixSamplesHeader}>
                  <span>类型样例</span>
                  <Button
                    loading={prefixSampleLoading}
                    size="small"
                    type="text"
                    onClick={() => loadPrefixSamples(selectedPrefix)}
                  >
                    刷新样例
                  </Button>
                </div>
                {prefixSamples.length ? (
                  <div className={styles.prefixSampleList}>
                    {prefixSamples.map((item) => (
                      <Button
                        className={styles.prefixSample}
                        key={`${item.type}-${item.name}`}
                        size="small"
                        type="text"
                        onClick={() => loadKeyDetail(item.name)}
                      >
                        <Tag color={getTypeColor(item.type || '')}>{item.type || 'unknown'}</Tag>
                        <span className={styles.prefixSampleName}>{item.name}</span>
                        <span className={styles.prefixSampleValue}>{String(item.value ?? '')}</span>
                      </Button>
                    ))}
                  </div>
                ) : !prefixSampleLoading ? (
                  <span className={styles.emptySamples}>未采样到可用 key</span>
                ) : null}
              </div>
            </div>
          </>
        ) : detail || detailLoading ? (
          <>
            <div className={styles.editorHeader}>
              <span>{creating ? '新增 Key' : '编辑器'}</span>
              <div className={styles.editorActions}>
                <Button
                  icon={<CloseOutlined />}
                  size="small"
                  onClick={() => {
                    setDetail(null);
                    setSelectedKey('');
                    setSelectedPrefix('');
                    setCreating(false);
                    setIsJsonString(false);
                  }}
                />
                <Button
                  icon={<SaveOutlined />}
                  loading={saving}
                  size="small"
                  type="primary"
                  onClick={saveKey}
                  disabled={(!detail && !creating) || !isEditableType(creating ? editorType : detail?.type)}
                >
                  应用
                </Button>
              </div>
            </div>
            <div className={styles.editorBody}>
              <div className={styles.formRow}>
                <span className={styles.label}>键名:</span>
                <Input value={editorKey} onChange={(event) => setEditorKey(event.target.value)} />
              </div>
              <div className={styles.formRow}>
                <span className={styles.label}>键类型:</span>
                {creating ? (
                  <Select
                    className={styles.typeSelect}
                    value={editorType}
                    options={KEY_TYPES.map((type) => ({ label: type, value: type }))}
                    onChange={(value) => {
                      setEditorType(value);
                      setTextValue('');
                      setIsJsonString(false);
                      setHashFields(value === 'hash' ? [{ id: uuid(), field: '', value: '' }] : []);
                      setHashFieldFilter('');
                    }}
                  />
                ) : (
                  <Tag color={getTypeColor(detail?.type || '')}>{detail?.type || '-'}</Tag>
                )}
              </div>
              <div className={styles.formRow}>
                <span className={styles.label}>值:</span>
                <div className={styles.valueEditor}>{renderValueEditor()}</div>
              </div>
              <div className={styles.formRow}>
                <span className={styles.label}>TTL:</span>
                <InputNumber
                  className={styles.ttlInput}
                  min={-1}
                  placeholder="无 TTL"
                  value={editorTtl}
                  onChange={(value) => setEditorTtl(value === null ? NO_TTL : Number(value))}
                />
                <span className={styles.ttlHint}>秒，-1 表示无 TTL</span>
              </div>
            </div>
          </>
        ) : (
          <Empty
            className={styles.emptyEditor}
            description="选择一个 Redis key 后编辑"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </div>
    </div>
  );

  function renderValueEditor() {
    const type = normalizeType(creating ? editorType : detail?.type);
    if (detailLoading) {
      return <div className={styles.editorLoading}>加载中...</div>;
    }
    if (!isEditableType(type)) {
      return <div className={styles.readonlyTip}>暂不支持编辑 {type || 'unknown'} 类型</div>;
    }
    if (type === 'hash') {
      return (
        <>
          <div className={styles.hashFilterBar}>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="过滤字段或值"
              size="small"
              value={hashFieldFilter}
              onChange={(event) => setHashFieldFilter(event.target.value)}
            />
            <span>
              {filteredHashFields.length}/{hashFields.length} 个字段
            </span>
          </div>
          <Table columns={hashColumns} dataSource={filteredHashFields} pagination={false} rowKey="id" size="small" />
          <div className={styles.fieldToolbar}>
            <Button icon={<PlusOutlined />} size="small" onClick={addHashField}>
              字段
            </Button>
            <span>{hashFields.length} 个字段</span>
          </div>
        </>
      );
    }
    return (
      <>
        {type === 'string' && (
          <div className={styles.jsonToolbar}>
            <Button
              icon={<CodeOutlined />}
              size="small"
              onClick={() => {
                const formattedJson = formatJson(textValue);
                if (formattedJson === null) {
                  message.warning('当前字符串不是合法 JSON');
                  return;
                }
                setTextValue(formattedJson);
                setIsJsonString(true);
              }}
            >
              格式化 JSON
            </Button>
            {isJsonString && <span>JSON 编辑模式</span>}
          </div>
        )}
        <Input.TextArea
          value={textValue}
          onChange={(event) => setTextValue(event.target.value)}
          rows={type === 'string' ? 5 : 7}
          placeholder={type === 'string' ? '字符串值' : '每行一个元素'}
        />
      </>
    );
  }
});

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return null;
  }
}

function buildRows(keys: IRedisKeyItem[], prefixCommentMap: Record<string, IRedisKeyPrefixItem>) {
  const root: IRedisKeyRow[] = [];
  const groupMap = new Map<string, IRedisKeyRow>();
  const prefixCountMap = buildPrefixCountMap(keys.map((item) => item.name));

  keys.forEach((item) => {
    const prefix = findLongestPrefix(item.name, prefixCountMap);
    if (!prefix) {
      root.push(toLeaf(item, item.name, getClosestPrefixComment(item.name, prefixCommentMap)));
      return;
    }

    let group = groupMap.get(prefix);
    if (!group) {
      group = {
        rowKey: `group-${prefix}`,
        name: prefix,
        displayName: prefix,
        isGroup: true,
        count: 0,
        comment: getPrefixComment(prefix, prefixCommentMap),
        keyCount: getPrefixKeyCount(prefix, prefixCommentMap),
        children: [],
      };
      groupMap.set(prefix, group);
      root.push(group);
    }
    group.comment = getPrefixComment(prefix, prefixCommentMap);
    group.keyCount = getPrefixKeyCount(prefix, prefixCommentMap);
    group.count = (group.count || 0) + 1;
    group.children!.push(toLeaf(item, item.name.slice(prefix.length) || item.name, group.comment));
  });

  return root;
}

function buildPrefixRows(prefixes: IRedisKeyPrefixItem[]) {
  return prefixes.map<IRedisKeyRow>((item) => ({
    rowKey: `prefix-${item.name}`,
    name: item.name,
    displayName: item.name,
    isGroup: true,
    prefixOnly: true,
    count: item.keyCount,
    keyCount: item.keyCount,
    sampleTypes: item.sampleTypes?.split(',').filter(Boolean),
    comment: item.comment,
  }));
}

function mergePrefixComments(
  current: Record<string, IRedisKeyPrefixItem>,
  items: IRedisKeyPrefixItem[],
): Record<string, IRedisKeyPrefixItem> {
  if (!items.length) {
    return current;
  }
  const next = { ...current };
  items.forEach((item) => {
    next[item.name] = {
      ...(next[item.name] || {}),
      ...item,
    };
  });
  return next;
}

function getClosestPrefixComment(keyName: string, prefixCommentMap: Record<string, IRedisKeyPrefixItem>) {
  const candidates = extractPrefixCandidates(keyName).sort((a, b) => b.length - a.length);
  for (const prefix of candidates) {
    const comment = getPrefixComment(prefix, prefixCommentMap);
    if (comment) {
      return comment;
    }
  }
  return undefined;
}

// Keep visible key grouping aligned with backend prefix extraction.
// Prefixes are delimiter-bound, longest-first, and skip single-key groups.
function buildPrefixCountMap(keys: string[]) {
  const candidateCountMap = new Map<string, number>();
  keys.forEach((key) => {
    extractPrefixCandidates(key).forEach((prefix) => {
      candidateCountMap.set(prefix, (candidateCountMap.get(prefix) || 0) + 1);
    });
  });

  const selected = new Map<string, number>();
  [...candidateCountMap.entries()]
    .filter(([, count]) => count > 1)
    .sort(([prefixA], [prefixB]) => prefixB.length - prefixA.length || prefixA.localeCompare(prefixB))
    .forEach(([prefix, count]) => {
      const hasContainingPrefix = [...selected.keys()].some(
        (selectedPrefix) => selectedPrefix.startsWith(prefix) || prefix.startsWith(selectedPrefix),
      );
      if (!hasContainingPrefix) {
        selected.set(prefix, count);
      }
    });
  return selected;
}

function extractPrefixCandidates(keyName: string) {
  const candidates: string[] = [];
  for (let index = 1; index < keyName.length - 1; index++) {
    if (REDIS_PREFIX_DELIMITERS.includes(keyName[index])) {
      candidates.push(keyName.slice(0, index + 1));
    }
  }
  return candidates;
}

function findLongestPrefix(keyName: string, prefixCountMap: Map<string, number>) {
  return [...prefixCountMap.keys()]
    .filter((prefix) => keyName.startsWith(prefix))
    .sort((prefixA, prefixB) => prefixB.length - prefixA.length)[0];
}

function getPrefixComment(prefix: string, prefixCommentMap: Record<string, IRedisKeyPrefixItem>) {
  return prefixCommentMap[prefix]?.comment || prefixCommentMap[trimTrailingDelimiter(prefix)]?.comment;
}

function getPrefixKeyCount(prefix: string, prefixCommentMap: Record<string, IRedisKeyPrefixItem>) {
  return prefixCommentMap[prefix]?.keyCount ?? prefixCommentMap[trimTrailingDelimiter(prefix)]?.keyCount;
}

function trimTrailingDelimiter(prefix: string) {
  if (!prefix || !REDIS_PREFIX_DELIMITERS.includes(prefix[prefix.length - 1])) {
    return prefix;
  }
  return prefix.slice(0, -1);
}

// 获取叶子节点的排序值
function getLeafValue(node: IRedisKeyRow, field: string): number | string {
  if (field === 'name' || field === 'displayName') {
    return node.displayName || '';
  }
  if (field === 'type') {
    return node.type || '';
  }
  if (field === 'size') {
    return node.size || 0;
  }
  if (field === 'ttl') {
    return node.ttl === undefined || node.ttl === null ? -1 : node.ttl;
  }
  if (field === 'keyCount') {
    return node.keyCount ?? node.count ?? 0;
  }
  return '';
}

// 获取分组节点的聚合排序值（size 取子节点总和，TTL 取最小值）
function getGroupAggValue(node: IRedisKeyRow, field: string): number | string {
  if (field === 'name' || field === 'displayName' || field === 'type') {
    return node.displayName || '';
  }
  if (field === 'size') {
    let total = 0;
    const stack: IRedisKeyRow[] = [node];
    while (stack.length) {
      const cur = stack.pop()!;
      if (cur.isGroup && cur.children) {
        stack.push(...cur.children);
      } else {
        total += cur.size || 0;
      }
    }
    return total;
  }
  if (field === 'ttl') {
    let minTtl = Infinity;
    const stack: IRedisKeyRow[] = [node];
    while (stack.length) {
      const cur = stack.pop()!;
      if (cur.isGroup && cur.children) {
        stack.push(...cur.children);
      } else {
        const t = cur.ttl === undefined || cur.ttl === null ? -1 : cur.ttl;
        if (t < minTtl) {
          minTtl = t;
        }
      }
    }
    return minTtl === Infinity ? -1 : minTtl;
  }
  if (field === 'keyCount') {
    return node.keyCount ?? node.count ?? 0;
  }
  return '';
}

// 递归排序树形数据：每层 children 按指定字段和方向排序，同级分组始终排在叶子前面
function sortTree(tree: IRedisKeyRow[], field: string, order: 'ascend' | 'descend'): IRedisKeyRow[] {
  const sorted = tree.map((node) => {
    if (node.isGroup && node.children) {
      return { ...node, children: sortTree(node.children, field, order) };
    }
    return node;
  });

  sorted.sort((a, b) => {
    let cmp = 0;
    if (a.isGroup && b.isGroup) {
      const valA = getGroupAggValue(a, field);
      const valB = getGroupAggValue(b, field);
      if (typeof valA === 'string' || typeof valB === 'string') {
        cmp = String(valA).localeCompare(String(valB));
      } else {
        cmp = (valA as number) - (valB as number);
      }
    } else if (a.isGroup) {
      cmp = -1;
    } else if (b.isGroup) {
      cmp = 1;
    } else {
      const valA = getLeafValue(a, field);
      const valB = getLeafValue(b, field);
      if (typeof valA === 'string' || typeof valB === 'string') {
        cmp = String(valA).localeCompare(String(valB));
      } else {
        cmp = (valA as number) - (valB as number);
      }
    }
    return order === 'ascend' ? cmp : -cmp;
  });

  return sorted;
}

function toLeaf(item: IRedisKeyItem, displayName: string, inheritedComment?: string): IRedisKeyRow {
  return {
    ...item,
    rowKey: `key-${item.name}`,
    displayName,
    inheritedComment,
  };
}

function formatPrefixBuildStatus(status?: IRedisKeyPrefixBuildStatus | null) {
  if (!status?.status) {
    return '前缀缓存: -';
  }
  const base = `前缀缓存: ${status.status}`;
  if (status.status === RUNNING_STATUS) {
    return `${base} · 已扫描 ${status.scannedCount || 0} · 前缀 ${status.prefixCount || 0}`;
  }
  if (status.status === 'FAILED') {
    return `${base}${status.errorMessage ? ` · ${status.errorMessage}` : ''}`;
  }
  if (status.prefixCount !== undefined) {
    return `${base} · 前缀 ${status.prefixCount}`;
  }
  return base;
}

function flattenPrefixRows(rows: IRedisKeyRow[]) {
  const result: IRedisKeyRow[] = [];
  const stack = [...rows];
  while (stack.length) {
    const item = stack.shift()!;
    if (item.isGroup) {
      result.push(item);
    }
    if (item.children?.length) {
      stack.push(...item.children);
    }
  }
  return result;
}

function collectSampleKeys(row: IRedisKeyRow) {
  if (!row.isGroup) {
    return [row.name];
  }
  const result: string[] = [];
  const stack = [...(row.children || [])];
  while (stack.length && result.length < 5) {
    const item = stack.shift()!;
    if (item.isGroup && item.children?.length) {
      stack.push(...item.children);
    } else if (item.name) {
      result.push(item.name);
    }
  }
  return result;
}

function getPrefixCount(prefix: string, prefixCommentMap: Record<string, IRedisKeyPrefixItem>, rows: IRedisKeyRow[]) {
  const cachedCount = getPrefixKeyCount(prefix, prefixCommentMap);
  if (cachedCount !== undefined) {
    return `${cachedCount} keys`;
  }
  const stack = [...rows];
  while (stack.length) {
    const item = stack.pop()!;
    if (item.name === prefix) {
      return `${item.count || 0} keys`;
    }
    if (item.children) {
      stack.push(...item.children);
    }
  }
  return '-';
}

function getTypeColor(type: string) {
  const colors: Record<string, string> = {
    string: 'blue',
    hash: 'green',
    list: 'purple',
    set: 'orange',
    zset: 'cyan',
    stream: 'geekblue',
  };
  return colors[type] || 'default';
}

function normalizeType(type?: string) {
  return (type || '').toLowerCase();
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function isEditableType(type?: string) {
  return ['string', 'hash', 'list', 'set', 'zset'].includes(normalizeType(type));
}

function buildEditorValue(type: string, textValue: string, hashFields: IHashFieldRow[]) {
  if (type === 'hash') {
    return hashFields.reduce<Record<string, string>>((result, item) => {
      const field = item.field.trim();
      if (field) {
        result[field] = item.value;
      }
      return result;
    }, {});
  }
  if (type === 'list' || type === 'set' || type === 'zset') {
    return textValue.split('\n').filter((item) => item.length > 0);
  }
  return textValue;
}

function formatTtl(ttl?: number) {
  if (ttl === undefined || ttl === null || ttl === -1) {
    return '(无 TTL)';
  }
  if (ttl === -2) {
    return '已过期';
  }

  const units = [
    [24 * 60 * 60, 'd'],
    [60 * 60, 'h'],
    [60, 'm'],
    [1, 's'],
  ] as const;
  let remaining = ttl;

  return units
    .map(([seconds, suffix]) => {
      const value = Math.floor(remaining / seconds);
      remaining %= seconds;
      return value > 0 || seconds === 1 ? `${value}${suffix}` : '';
    })
    .filter(Boolean)
    .join(' ');
}

function formatSize(size?: number) {
  if (!size) {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function computeHashDiff(
  original: Record<string, string>,
  current: IHashFieldRow[],
): { addedFields: Record<string, string>; removedFields: string[] } {
  const currentMap: Record<string, string> = {};
  current.forEach((row) => {
    const field = row.field.trim();
    if (field) {
      currentMap[field] = row.value;
    }
  });

  const addedFields: Record<string, string> = {};
  const removedFields: string[] = [];

  for (const [field, value] of Object.entries(currentMap)) {
    if (!(field in original) || original[field] !== value) {
      addedFields[field] = value;
    }
  }

  for (const field of Object.keys(original)) {
    if (!(field in currentMap)) {
      removedFields.push(field);
    }
  }

  return { addedFields, removedFields };
}

export default RedisDataView;

import React, { useState, useEffect, useRef } from 'react';
import i18n from '@/i18n';
import { Input, Dropdown, Modal, Pagination } from 'antd';
import Iconfont from '@/components/Iconfont';
import LoadingContent from '@/components/Loading/LoadingContent';
import historyServer from '@/service/history';
import { ConsoleOpenedStatus, workspaceTabConfig } from '@/constants';
import { IConsole, ITreeNode } from '@/typings';
import styles from './index.less';
import { approximateList } from '@/utils';
import { addWorkspaceTab, getSavedConsoleList } from '@/pages/main/workspace/store/console';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import MenuLabel from '@/components/MenuLabel';
import { useConnectionStore } from '@/pages/main/store/connection';

const SaveList = () => {
  const [searching, setSearching] = useState<boolean>(false);
  const inputRef = useRef<any>();
  const [searchedList, setSearchedList] = useState<ITreeNode[] | undefined>();
  const leftModuleTitleRef = useRef<any>(null);
  const saveBoxListRef = useRef<any>(null);
  const savedConsoleList = useWorkspaceStore((state) => state.savedConsoleList);
  const savedConsoleTotal = useWorkspaceStore((state) => state.savedConsoleTotal);
  const [pageNo, setPageNo] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(100);
  const { connectionList } = useConnectionStore((state) => {
    return {
      connectionList: state.connectionList,
    };
  });
  const [editData, setEditData] = useState<any>(null);

  const getConnectionEnvironment = (dataSourceId?: number) => {
    if (!dataSourceId || !connectionList) return null;
    const connection = connectionList.find((item) => item.id === dataSourceId);
    return connection?.environment || null;
  };

  useEffect(() => {
    getSavedConsoleList(pageNo, pageSize);
  }, [pageNo, pageSize]);

  useEffect(() => {
    if (searching) {
      inputRef.current!.focus({
        cursor: 'start',
      });
    }
  }, [searching]);

  function openSearch() {
    setSearching(true);
  }

  function onBlur() {
    if (!inputRef.current.input.value) {
      setSearching(false);
      setSearchedList(undefined);
    }
  }

  function onChange(value: string) {
    if (savedConsoleList) {
      setSearchedList(approximateList(savedConsoleList as any, value));
    }
  }

  function openConsole(item: IConsole) {
    const params: any = {
      id: item.id,
      tabOpened: ConsoleOpenedStatus.IS_OPEN,
    };
    historyServer.updateSavedConsole(params).then(() => {
      addWorkspaceTab({
        id: item.id,
        type: item.operationType,
        title: item.name,
        uniqueData: {
          dataSourceId: item.dataSourceId,
          dataSourceName: item.dataSourceName,
          databaseType: item.type,
          databaseName: item.databaseName,
          schemaName: item.schemaName,
          status: item.status,
          ddl: item.ddl,
          connectable: item.connectable,
        },
      });
    });
  }

  function deleteSaved(data: IConsole) {
    const params: any = {
      id: data.id,
    };
    historyServer.deleteSavedConsole(params).then(() => {
      getSavedConsoleList(pageNo, pageSize);
    });
  }

  const editSaved = (data: IConsole) => {
    setEditData(data);
  };

  const handlePageChange = (page: number) => {
    setPageNo(page);
  };

  const handlePageSizeChange = (current: number, size: number) => {
    setPageSize(size);
    setPageNo(1);
  };

  return (
    <>
      <div className={styles.saveModule}>
        <div ref={leftModuleTitleRef} className={styles.leftModuleTitle}>
          {searching ? (
            <div className={styles.leftModuleTitleSearch}>
              <Input
                ref={inputRef}
                size="small"
                placeholder={i18n('common.text.search')}
                prefix={<Iconfont code="&#xe600;" />}
                onBlur={onBlur}
                onChange={(e) => onChange(e.target.value)}
                allowClear
              />
            </div>
          ) : (
            <div className={styles.leftModuleTitleText}>
              <div className={styles.modelName}>{i18n('workspace.title.savedConsole')}</div>
              <div className={styles.iconBox}>
                {/* <div className={styles.refreshIcon} onClick={() => refreshTableList()}>
                  <Iconfont code="&#xec08;" />
                </div> */}
                <div className={styles.searchIcon} onClick={() => openSearch()}>
                  <Iconfont code="&#xe600;" />
                </div>
              </div>
            </div>
          )}
        </div>
        <div ref={saveBoxListRef} className={styles.saveBoxList}>
          <LoadingContent className={styles.loadingContent} data={savedConsoleList} handleEmpty>
            {(searchedList || savedConsoleList)?.map((t) => {
              const environment = getConnectionEnvironment(t.dataSourceId);
              return (
                <Dropdown
                  key={t.id}
                  trigger={['contextMenu']}
                  menu={{
                    items: [
                      {
                        key: 'open',
                        label: <MenuLabel icon="&#xec83;" label={i18n('common.button.open')} />,
                        onClick: () => {
                          openConsole(t);
                        },
                      },
                      {
                        key: 'edit',
                        label: <MenuLabel icon="&#xe602;" label={i18n('common.text.rename')} />,
                        onClick: () => {
                          editSaved(t);
                        },
                      },
                      {
                        key: 'delete',
                        label: <MenuLabel icon="&#xe6a7;" label={i18n('common.button.delete')} />,
                        onClick: () => {
                          deleteSaved(t);
                        },
                      },
                    ],
                  }}
                >
                  <div
                    onDoubleClick={() => {
                      openConsole(t);
                    }}
                    className={styles.saveItem}
                  >
                    <div className={styles.saveItemText}>
                      {environment && <span className={styles.envTag} style={{ background: environment.color?.toLocaleLowerCase() }} />}
                      <div className={styles.iconBox}>
                        <Iconfont code={workspaceTabConfig[t.operationType]?.icon} />
                      </div>
                      <div className={styles.itemName} dangerouslySetInnerHTML={{ __html: t.name }} />
                    </div>
                  </div>
                </Dropdown>
              );
            })}
          </LoadingContent>
        </div>
        <div className={styles.paginationWrapper}>
          <Pagination
            current={pageNo}
            pageSize={pageSize}
            total={savedConsoleTotal}
            onChange={handlePageChange}
            onShowSizeChange={handlePageSizeChange}
            showSizeChanger
            showQuickJumper
            showTotal={(total) => `共 ${total} 条`}
          />
        </div>
      </div>
      <Modal
        title={i18n('common.text.rename')}
        open={!!editData}
        onOk={() => {
          const params: any = {
            id: editData.id,
            name: editData.name,
          };
          historyServer.updateSavedConsole(params).then(() => {
            getSavedConsoleList();
            setEditData(null);
          });
        }}
        onCancel={() => setEditData(null)}
      >
        <Input
          value={editData?.name}
          onChange={(e) => {
            setEditData({
              ...editData,
              name: e.target.value,
            });
          }}
        />
      </Modal>
    </>
  );
};

export default SaveList;

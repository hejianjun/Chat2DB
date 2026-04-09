import ExecuteSQL from '@/components/ExecuteSQL';
import LoadingContent from '@/components/Loading/LoadingContent';
import { DatabaseTypeCode, WorkspaceTabType } from '@/constants';
import i18n from '@/i18n';
import sqlService, { IModifyTableSqlParams } from '@/service/sql';
import { IColumnTypes, IEditTableInfo, IWorkspaceTab } from '@/typings';
import connectToEventSource from '@/utils/eventSource';
import { formatParams } from '@/utils/url';
import { Button, Drawer, Modal, Spin, message } from 'antd';
import classnames from 'classnames';
import lodash from 'lodash';
import React, { createContext, memo, useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { v4 as uuidv4 } from 'uuid';
import BaseInfo, { IBaseInfoRef } from './BaseInfo';
import ColumnList, { IColumnListRef } from './ColumnList';
import ForeignKeyList, { IForeignKeyListRef } from './ForeignKeyList';
import styles from './index.less';
import IndexList, { IIndexListRef } from './IndexList';

interface IProps {
  dataSourceId: number;
  databaseName: string;
  schemaName?: string | null;
  tableName?: string;
  databaseType: DatabaseTypeCode;
  changeTabDetails: (data: IWorkspaceTab) => void;
  tabDetails: IWorkspaceTab;
  submitCallback: () => void;
}
enum IPromptType {
  NL_2_SQL = 'NL_2_SQL',
  SQL_EXPLAIN = 'SQL_EXPLAIN',
  SQL_OPTIMIZER = 'SQL_OPTIMIZER',
  SQL_2_SQL = 'SQL_2_SQL',
  NL_2_COMMENT = 'NL_2_COMMENT',
  ChatRobot = 'ChatRobot',
}

interface ITabItem {
  index: number;
  title: string;
  key: string;
  component: any; // TODO: 组件的Ts是什么
}

interface IContext extends IProps {
  tableDetails: IEditTableInfo;
  baseInfoRef: React.RefObject<IBaseInfoRef>;
  columnListRef: React.RefObject<IColumnListRef>;
  indexListRef: React.RefObject<IIndexListRef>;
  foreignKeyListRef: React.RefObject<IForeignKeyListRef>;
  databaseSupportField: IDatabaseSupportField;
}

export const Context = createContext<IContext>({} as any);

interface IOption {
  label: string;
  value: string | number | null;
}

// 列字段类型，select组件的options需要的数据结构
interface IColumnTypesOption extends IColumnTypes {
  label: string;
  value: string | number | null;
}
export interface IDatabaseSupportField {
  columnTypes: IColumnTypesOption[];
  charsets: IOption[];
  collations: IOption[];
  indexTypes: IOption[];
  defaultValues: IOption[];
}

export default memo((props: IProps) => {
  const {
    databaseName,
    dataSourceId,
    tableName,
    schemaName,
    changeTabDetails,
    tabDetails,
    databaseType,
    submitCallback,
  } = props;
  const [tableDetails, setTableDetails] = useState<IEditTableInfo>({} as any);
  const [oldTableDetails, setOldTableDetails] = useState<IEditTableInfo>({} as any);
  const [viewSqlModal, setViewSqlModal] = useState<boolean>(false);
  const baseInfoRef = useRef<IBaseInfoRef>(null);
  const columnListRef = useRef<IColumnListRef>(null);
  const indexListRef = useRef<IIndexListRef>(null);
  const foreignKeyListRef = useRef<IForeignKeyListRef>(null);
  const [appendValue, setAppendValue] = useState<string>('');
  const [isAiDrawerLoading, setIsAiDrawerLoading] = useState(false);
  const [isAiDrawerOpen, setIsAiDrawerOpen] = useState(false);
  const [isStream, setIsStream] = useState(false);
  const [aiContent, setAiContent] = useState('');
  const chatResult = useRef('');
  const tabList = useMemo(() => {
    return [
      {
        index: 0,
        title: i18n('editTable.tab.basicInfo'),
        key: 'basic',
        component: <BaseInfo ref={baseInfoRef} />,
      },
      {
        index: 1,
        title: i18n('editTable.tab.columnInfo'),
        key: 'column',
        component: <ColumnList ref={columnListRef} />,
      },
      {
        index: 2,
        title: i18n('editTable.tab.indexInfo'),
        key: 'index',
        component: <IndexList ref={indexListRef} />,
      },
      {
        index: 3,
        title: i18n('editTable.tab.foreignKeyInfo'),
        key: 'foreignKey',
        component: <ForeignKeyList ref={foreignKeyListRef} />,
      },
    ];
  }, []);
  const [currentTab, setCurrentTab] = useState<ITabItem>(tabList[0]);
  const [databaseSupportField, setDatabaseSupportField] = useState<IDatabaseSupportField>({
    columnTypes: [],
    charsets: [],
    collations: [],
    indexTypes: [],
    defaultValues: [],
  });
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const uid = useMemo(() => uuidv4(), []);
  const closeEventSource = useRef<any>();
  function changeTab(item: ITabItem) {
    setCurrentTab(item);
  }

  useEffect(() => {
    if (tableName) {
      getTableDetails();
    }
    getDatabaseFieldTypeList();
  }, []);

  // 获取数据库字段类型列表
  const getDatabaseFieldTypeList = () => {
    sqlService
      .getDatabaseFieldTypeList({
        dataSourceId,
        databaseName,
      })
      .then((res) => {
        const columnTypes =
          res?.columnTypes?.map((i) => {
            return {
              ...i,
              value: i.typeName,
              label: i.typeName,
            };
          }) || [];

        const charsets =
          res?.charsets?.map((i) => {
            return {
              value: i.charsetName,
              label: i.charsetName,
            };
          }) || [];

        const collations =
          res?.collations?.map((i) => {
            return {
              value: i.collationName,
              label: i.collationName,
            };
          }) || [];

        const indexTypes =
          res?.indexTypes?.map((i) => {
            return {
              value: i.typeName,
              label: i.typeName,
            };
          }) || [];

        const defaultValues =
          res?.defaultValues?.map((i) => {
            return {
              value: i.defaultValue,
              label: i.defaultValue,
            };
          }) || [];

        setDatabaseSupportField({
          columnTypes,
          charsets,
          collations,
          indexTypes,
          defaultValues,
        });
      });
  };

  const getTableDetails = (myParams?: { tableNameProps?: string }) => {
    const { tableNameProps } = myParams || {};
    const myTableName = tableNameProps || tableName;
    if (myTableName) {
      const params = {
        databaseName,
        dataSourceId,
        tableName: myTableName,
        schemaName,
        refresh: true,
      };
      setIsLoading(true);
      sqlService
        .getTableDetails(params)
        .then((res) => {
          const newTableDetails = lodash.cloneDeep(res);
          setTableDetails(newTableDetails || {});
          setOldTableDetails(res);
        })
        .finally(() => {
          setIsLoading(false);
        });
    }
  };

  function submit() {
    if (baseInfoRef.current && columnListRef.current && indexListRef.current) {
      const newTable = {
        ...oldTableDetails,
        ...baseInfoRef.current.getBaseInfo(),
        columnList: columnListRef.current.getColumnListInfo()!,
        indexList: indexListRef.current.getIndexListInfo()!,
        foreignKeyList: foreignKeyListRef.current?.getForeignKeyListInfo(),
      };

      const params: IModifyTableSqlParams = {
        databaseName,
        dataSourceId,
        schemaName,
        refresh: true,
        newTable,
      };

      if (tableName) {
        // params.tableName = tableName;
        params.oldTable = oldTableDetails;
      }
      sqlService.getModifyTableSql(params).then((res) => {
        setViewSqlModal(true);
        setAppendValue(res?.[0].sql);
      });
    }
  }

  function guess() {
    const handleError = (error: any) => {
      console.error('Error:', error);
      setIsLoading(false);
      closeEventSource.current();
    };
    const handleCallback = (_message: MessageEvent) => {
      setIsLoading(false);
      const toolFuntion = JSON.parse(_message.data);
      if ('set_table_comment' == toolFuntion.name) {
        // 设置表注释
        baseInfoRef.current?.setTableComment(JSON.parse(toolFuntion.arguments).comment);
      } else if ('set_column_comment' == toolFuntion.name) {
        // 设置列注释
        const columnArgs = JSON.parse(toolFuntion.arguments);
        columnListRef.current?.setColumnComment(columnArgs.columnName, columnArgs.comment);
      }
    }
    const handleMessage = (_message: string) => {
      setIsLoading(false);
      setIsAiDrawerLoading(false);
      try {
        const isEOF = _message === '[DONE]';
        if (isEOF) {
          closeEventSource.current();
          setIsStream(false);
          setIsAiDrawerLoading(false);
          chatResult.current += '\n';
          setAiContent(chatResult.current);
          chatResult.current = '';
          return
        }
        chatResult.current += JSON.parse(_message).content;
        setAiContent(chatResult.current);
      } catch (error) {
        setIsLoading(false);
        setIsStream(false);
        setIsAiDrawerLoading(false);
        closeEventSource.current();
      }
    };
    if (baseInfoRef.current && columnListRef.current && indexListRef.current) {
      const params = formatParams({
        databaseName,
        dataSourceId,
        schemaName,
        tableNames: tableName ? [tableName] : [],
        promptType: IPromptType.NL_2_COMMENT,
      });
      setAiContent('');
      setIsAiDrawerOpen(true);
      setIsAiDrawerLoading(true);
      closeEventSource.current = connectToEventSource({
        url: `/api/ai/chat?${params}`,
        uid,
        onOpen: () => {
          setIsLoading(true);
        },
        onMessage: handleMessage,
        onCallback: handleCallback,
        onError: handleError,
      });
    }
  }

  const executeSuccessCallBack = () => {
    setViewSqlModal(false);
    message.success(i18n('common.text.successfulExecution'));
    const newTableName = baseInfoRef.current?.getBaseInfo().name;
    getTableDetails({ tableNameProps: newTableName });
    if (!tableName) {
      changeTabDetails({
        ...tabDetails,
        title: `${newTableName}`,
        type: WorkspaceTabType.EditTable,
        uniqueData: {
          ...(tabDetails.uniqueData || {}),
          tableName: newTableName,
        },
      });
    }
    // 保存成功后，刷新左侧树
    submitCallback?.();
  };

  return (
    <Context.Provider
      value={{
        ...props,
        tableDetails,
        baseInfoRef,
        columnListRef,
        indexListRef,
        foreignKeyListRef,
        databaseSupportField,
        databaseType,
      }}
    >
      <LoadingContent coverLoading isLoading={isLoading} className={classnames(styles.box)}>
        <div className={styles.header}>
          <div className={styles.tabList} style={{ '--i': currentTab.index } as any}>
            {tabList.map((item) => {
              return (
                <div
                  key={item.key}
                  onClick={changeTab.bind(null, item)}
                  className={classnames(styles.tabItem, currentTab.key == item.key ? styles.currentTab : '')}
                >
                  {item.title}
                </div>
              );
            })}
          </div>
          <div className={styles.saveButton}>
            <Button type="link" onClick={guess}>
              {i18n('common.button.guess')}
            </Button>
          </div>
          <div className={styles.saveButton}>
            <Button type="primary" onClick={submit}>
              {i18n('common.button.save')}
            </Button>
          </div>
        </div>
        <div className={styles.main}>
          {tabList.map((t) => {
            return (
              <div key={t.key} className={classnames(styles.tab, { [styles.hidden]: currentTab.key !== t.key })}>
                {t.component}
              </div>
            );
          })}
        </div>
      </LoadingContent>
      <Drawer
        open={isAiDrawerOpen}
        getContainer={false}
        mask={false}
        onClose={() => {
          try {
            setIsAiDrawerOpen(false);
            setIsAiDrawerLoading(false);
            setIsStream(false);
            closeEventSource.current && closeEventSource.current();
          } catch (error) {
            console.error('close drawer', error);
          }
        }}
      >
        <Spin spinning={isAiDrawerLoading} style={{ height: '100%' }}>
          <div className={styles.aiBlock}>
            <ReactMarkdown>{aiContent}</ReactMarkdown>
          </div>
        </Spin>
      </Drawer>
      <Modal
        title={i18n('editTable.title.sqlPreview')}
        open={!!viewSqlModal}
        onCancel={() => {
          setViewSqlModal(false);
        }}
        width="60vw"
        maskClosable={false}
        footer={false}
        destroyOnClose={true}
      >
        <ExecuteSQL
          initSql={appendValue}
          databaseName={databaseName}
          dataSourceId={dataSourceId}
          tableName={tableName}
          schemaName={schemaName}
          databaseType={databaseType}
          executeSuccessCallBack={executeSuccessCallBack}
        />
      </Modal>
    </Context.Provider>
  );
});

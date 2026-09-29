import { useWorkspaceStore } from './index';
import { IBoundInfo, IConsole, ICreateConsoleParams } from '@/typings';
import { IWorkspaceTab } from '@/typings/workspace';
import historyService from '@/service/history';
import { ConsoleStatus, WorkspaceTabType } from '@/constants';
import { message } from 'antd';
import i18n from '@/i18n';

export interface IConsoleStore {
  consoleList: IConsole[] | null;
  savedConsoleList: IConsole[] | null;
  savedConsoleTotal: number;
  activeConsoleId: string | number | null;
  workspaceTabList: IWorkspaceTab[] | null;
  createConsoleLoading: boolean
}

export const initConsoleStore = {
  consoleList: null,
  savedConsoleList: null,
  savedConsoleTotal: 0,
  activeConsoleId: null,
  workspaceTabList: null,
  createConsoleLoading: false,
};

let savedConsoleRequestId = 0;

export const getOpenConsoleList = () => {
  historyService
    .getConsoleList({
      tabOpened: 'y',
      pageNo: 1,
      pageSize: 20,
    })
    .then((res) => {
      useWorkspaceStore.setState({ consoleList: res?.data });
    });
};

export const getSavedConsoleList = (pageNo: number = 1, pageSize: number = 100, searchKey?: string) => {
  const requestId = ++savedConsoleRequestId;
  const normalizedSearchKey = searchKey?.trim();

  return historyService
    .getConsoleList({
      pageNo,
      pageSize,
      searchKey: normalizedSearchKey || undefined,
      status: ConsoleStatus.RELEASE,
      orderByCreateDesc: true,
    })
    .then((res) => {
      if (requestId !== savedConsoleRequestId) {
        return res;
      }

      useWorkspaceStore.setState({
        savedConsoleList: res?.data,
        savedConsoleTotal: res?.total || 0,
      });
      return res;
    });
};

export const setActiveConsoleId = (id: IConsoleStore['activeConsoleId']) => {
  useWorkspaceStore.setState({ activeConsoleId: id });
};

export const setWorkspaceTabList = (items: IConsoleStore['workspaceTabList']) => {
  useWorkspaceStore.setState({ workspaceTabList: items });
};

export const updateConsoleBoundInfo = (
  consoleId: string | number,
  boundInfo: Pick<IBoundInfo, 'dataSourceId' | 'dataSourceName' | 'databaseType' | 'databaseName' | 'schemaName'>,
) => {
  useWorkspaceStore.setState((state) => ({
    consoleList: state.consoleList
      ? state.consoleList.map((item) =>
          String(item.id) === String(consoleId)
            ? {
                ...item,
                dataSourceId: boundInfo.dataSourceId,
                dataSourceName: boundInfo.dataSourceName,
                type: boundInfo.databaseType,
                databaseName: boundInfo.databaseName,
                schemaName: boundInfo.schemaName,
              }
            : item,
        )
      : state.consoleList,
    workspaceTabList: state.workspaceTabList
      ? state.workspaceTabList.map((item) =>
          String(item.id) === String(consoleId)
            ? {
                ...item,
                uniqueData: {
                  ...item.uniqueData,
                  dataSourceId: boundInfo.dataSourceId,
                  dataSourceName: boundInfo.dataSourceName,
                  databaseType: boundInfo.databaseType,
                  databaseName: boundInfo.databaseName,
                  schemaName: boundInfo.schemaName,
                },
              }
            : item,
        )
      : state.workspaceTabList,
  }));
};

export const createConsole = (params: ICreateConsoleParams) => {
  const workspaceTabList = useWorkspaceStore.getState().workspaceTabList;
  const currentConnectionDetails = useWorkspaceStore.getState().currentConnectionDetails;
  const newConsole = {
    ...params,
    name: params.name || `untitled-${params.databaseName || params.schemaName} (${params.dataSourceName})`,
    ddl: params.ddl || '',
    status: ConsoleStatus.DRAFT,
    operationType: params.operationType || WorkspaceTabType.CONSOLE,
    type: params.databaseType,
    supportDatabase: currentConnectionDetails?.supportDatabase,
    supportSchema: currentConnectionDetails?.supportSchema,
  };
  return new Promise((resolve) => {
    if ((workspaceTabList?.length || 0) >= 20) {
      message.warning(i18n('workspace.tips.maxConsole'));
      return;
    }
    useWorkspaceStore.setState({ createConsoleLoading: true });
    const savedConsole = { ...newConsole };
    delete savedConsole.initialResult;
    historyService.createConsole(savedConsole).then((res) => {
      const newConsoleItem: IConsole = {
        id: res,
        name: newConsole.name,
        ddl: newConsole.ddl,
        dataSourceId: newConsole.dataSourceId,
        dataSourceName: newConsole.dataSourceName,
        type: newConsole.type,
        databaseName: newConsole.databaseName,
        schemaName: newConsole.schemaName,
        status: newConsole.status,
        connectable: true,
        operationType: newConsole.operationType,
      };

      useWorkspaceStore.setState((state) => ({
        consoleList: [...(state.consoleList || []), newConsoleItem],
        workspaceTabList: [
          ...(state.workspaceTabList || []),
          {
            id: res,
            title: newConsole.name,
            type: newConsole.operationType,
            uniqueData: newConsole,
          },
        ],
        activeConsoleId: res,
      }));
      resolve(res);
    })
      .finally(() => { 
      useWorkspaceStore.setState({ createConsoleLoading: false });
    });
  });
};

export const addWorkspaceTab = (params: IWorkspaceTab) => {
  const workspaceTabList = useWorkspaceStore.getState().workspaceTabList;
  if (workspaceTabList?.findIndex((item) => item?.id === params?.id) !== -1) {
    setActiveConsoleId(params.id);
    return;
  }

  const newList = [...(workspaceTabList || []), params];

  setWorkspaceTabList(newList);
  setActiveConsoleId(params.id);
};

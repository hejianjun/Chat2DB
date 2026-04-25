import { useWorkspaceStore } from './index';
import { DatabaseTypeCode } from '@/constants';
import { CreateType } from '@/components/CreateDatabase';
import { IImportDataModalParams } from '@/components/ImportDataModal';

export interface IModalStore {
  openCreateDatabaseModal: ((params: {
    type: CreateType;
    relyOnParams: {
      databaseType: DatabaseTypeCode;
      dataSourceId: number;
      databaseName?: string;
    };
    executedCallback?: (status: true) => void;
  }) => void) | null;
  openImportDataModal: ((params: IImportDataModalParams) => void) | null;
}

export const initModalStore: IModalStore = {
  openCreateDatabaseModal: null,
  openImportDataModal: null,
};

export const setOpenCreateDatabaseModal = (fn: any) => {
  useWorkspaceStore.setState({ openCreateDatabaseModal: fn });
};

export const setOpenImportDataModal = (fn: any) => {
  useWorkspaceStore.setState({ openImportDataModal: fn });
};

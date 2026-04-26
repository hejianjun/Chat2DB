import { useWorkspaceStore } from './index';
import { DatabaseTypeCode } from '@/constants';
import { CreateType } from '@/components/CreateDatabase';
import { IImportDataModalParams } from '@/components/ImportDataModal';
import { IExportDataModalParams } from '@/components/ExportDataModal';

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
  openExportDataModal: ((params: IExportDataModalParams) => void) | null;
}

export const initModalStore: IModalStore = {
  openCreateDatabaseModal: null,
  openImportDataModal: null,
  openExportDataModal: null,
};

export const setOpenCreateDatabaseModal = (fn: any) => {
  useWorkspaceStore.setState({ openCreateDatabaseModal: fn });
};

export const setOpenImportDataModal = (fn: any) => {
  useWorkspaceStore.setState({ openImportDataModal: fn });
};

export const setOpenExportDataModal = (fn: any) => {
  useWorkspaceStore.setState({ openExportDataModal: fn });
};

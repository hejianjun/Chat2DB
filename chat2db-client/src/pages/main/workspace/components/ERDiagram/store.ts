/**
 * ER图状态管理Store
 * 使用Zustand管理ER图的状态，包括数据、加载状态、过滤条件、布局类型等
 */
import { create } from 'zustand';
import sqlServer, { IErDiagram, IErParams } from '@/service/sql';

/** 布局类型：力导向布局或层级布局 */
export type LayoutType = 'force' | 'dagre';

/** ER图Store接口定义 */
interface IErDiagramStore {
  /** ER图数据，包含节点和边 */
  erDiagramData: IErDiagram | null;
  /** 数据加载状态 */
  loading: boolean;
  /** 表名过滤文本 */
  filterText: string;
  /** 当前布局类型 */
  layoutType: LayoutType;
  /** 是否包含虚拟外键 */
  includeVirtualFk: boolean;
  /** 当前选中的表ID */
  selectedTableId: string | null;

  /** 获取ER图数据 */
  fetchErDiagram: (params: IErParams) => Promise<void>;
  /** 推断虚拟外键 */
  inferVirtualForeignKeys: (params: IErParams) => Promise<number>;
  /** 删除虚拟外键 */
  deleteVirtualForeignKey: (edgeId: string, params: IErParams) => Promise<void>;
  /** 设置过滤文本 */
  setFilterText: (text: string) => void;
  /** 设置布局类型 */
  setLayoutType: (type: LayoutType) => void;
  /** 设置选中的表ID */
  setSelectedTableId: (id: string | null) => void;
  /** 设置是否包含虚拟外键 */
  setIncludeVirtualFk: (value: boolean) => void;
}

const useErDiagramStore = create<IErDiagramStore>((set, get) => ({
  erDiagramData: null,
  loading: false,
  filterText: '',
  layoutType: 'dagre',
  includeVirtualFk: true,
  selectedTableId: null,

  fetchErDiagram: async (params: IErParams) => {
    set({ loading: true });
    try {
      const res = await sqlServer.getErDiagram(params);
      set({ erDiagramData: res });
    } catch (error) {
      console.error('Failed to fetch ER diagram data:', error);
    } finally {
      set({ loading: false });
    }
  },

  inferVirtualForeignKeys: async (params: IErParams) => {
    try {
      const count = await sqlServer.inferVirtualForeignKeys(params);
      // 推断完成后刷新ER图
      await get().fetchErDiagram(params);
      return count;
    } catch (error) {
      console.error('Failed to infer virtual foreign keys:', error);
      throw error;
    }
  },

  deleteVirtualForeignKey: async (edgeId: string, params: IErParams) => {
    try {
      // 从ER图数据中找到对应的虚拟外键
      const currentData = get().erDiagramData;
      if (currentData) {
        const edge = currentData.edges.find(e => e.id === edgeId);
        if (edge && edge.virtual) {
          // 通过名称匹配删除
          await sqlServer.deleteVirtualForeignKey({
            dataSourceId: params.dataSourceId,
            databaseName: params.databaseName,
            schemaName: params.schemaName,
            tableName: edge.source,
            keyName: edge.id,
          });
          // 删除完成后刷新ER图
          await get().fetchErDiagram(params);
        }
      }
    } catch (error) {
      console.error('Failed to delete virtual foreign key:', error);
      throw error;
    }
  },

  setFilterText: (text: string) => set({ filterText: text }),
  setLayoutType: (type: LayoutType) => set({ layoutType: type }),
  setSelectedTableId: (id: string | null) => set({ selectedTableId: id }),
  setIncludeVirtualFk: (value: boolean) => set({ includeVirtualFk: value }),
}));

export default useErDiagramStore;

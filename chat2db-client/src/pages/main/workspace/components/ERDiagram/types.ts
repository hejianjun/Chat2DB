
export interface TableColumn {
  name: string;
  dataType: string;
  dataLength?: number;
  dataScale?: number;
  nullable: boolean;
  defaultValue?: string;
  comment?: string;
  isPrimaryKey: boolean;
  isAutoIncrement: boolean;
  isForeignKey: boolean;
  referencedTable?: string;
  referencedColumn?: string;
}

export interface TableIndex {
  name: string;
  type: 'PRIMARY' | 'UNIQUE' | 'INDEX';
  columns: string[];
}

export interface TableNode {
  id: string;
  name: string;
  schema?: string;
  comment?: string;
  columns: TableColumn[];
  indexes: TableIndex[];
  color?: string;
}

export interface RelationEnd {
  tableId: string;
  columnName: string;
}

export interface RelationEdge {
  id: string;
  source: RelationEnd;
  target: RelationEnd;
  type: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_MANY';
  name?: string;
  description?: string;
}

export interface Position {
  x: number;
  y: number;
}

export interface LayoutInfo {
  type: 'FORCE' | 'HIERARCHICAL' | 'ORTHOGONAL' | 'CIRCULAR';
  positions?: Map&lt;string, Position&gt;;
}

export interface DatabaseInfo {
  name: string;
  type: string;
  tableCount: number;
}

export interface ERDiagramData {
  tables: TableNode[];
  relations: RelationEdge[];
  databaseInfo: DatabaseInfo;
  layout: LayoutInfo;
}

// 向后兼容的旧数据结构
export interface LegacyNode {
  id: string;
  name: string;
}

export interface LegacyEdge {
  id: string;
  source: string;
  target: string;
  description?: string;
}

export interface LegacyErDiagramData {
  nodes?: LegacyNode[];
  edges?: LegacyEdge[];
}

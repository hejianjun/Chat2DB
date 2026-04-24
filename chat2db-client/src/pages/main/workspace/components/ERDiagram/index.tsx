/**
 * ER图主组件
 * 使用ReactFlow渲染数据库表之间的外键关系图
 * 支持表过滤、布局切换、虚拟外键显示、缩放控制、PNG导出等功能
 */
import React, { useCallback, useEffect, useRef } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  Node,
  Edge,
  OnNodeClick,
  OnPaneClick,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from '@dagrejs/dagre';
import { toPng } from 'html-to-image';
import { Spin, Empty } from 'antd';
import i18n from '@/i18n';
import useErDiagramStore, { LayoutType } from './store';
import TableNode from './components/TableNode';
import RelationEdge from './components/RelationEdge';
import Toolbar from './components/Toolbar';
import TableFilter from './components/TableFilter';
import Legend from './components/Legend';
import styles from './index.less';

/** ER图组件Props */
interface IERDiagramProps {
  uniqueData: {
    databaseName: string;
    dataSourceId: number;
    schemaName?: string;
    tableName: string;
  };
}

/** 自定义节点和边类型映射 */
const nodeTypes = { tableNode: TableNode };
const edgeTypes = { relationEdge: RelationEdge };

/** dagre图对象，用于层级布局计算 */
const dagreGraph = new dagre.graphlib.Graph({ multigraph: true, compound: false });
dagreGraph.setDefaultEdgeLabel(() => ({}));

/**
 * 使用dagre算法计算层级布局
 * @param nodes 节点列表
 * @param edges 边列表
 * @param direction 布局方向，默认从上到下
 * @returns 计算好位置的节点列表
 */
const getDagreLayout = (nodes: Node[], edges: Edge[], direction: 'TB' | 'LR' = 'TB') => {
  dagreGraph.setGraph({ rankdir: direction, nodesep: 50, ranksep: 80 });

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: 160, height: 60 });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  return nodes.map((node) => {
    const pos = dagreGraph.node(node.id);
    return {
      ...node,
      position: { x: pos.x - 80, y: pos.y - 30 },
    };
  });
};

/**
 * 使用力导向算法计算布局
 * 节点之间相互排斥，有边连接的节点相互吸引
 * @param nodes 节点列表
 * @param edges 边列表
 * @returns 计算好位置的节点列表
 */
const getForceLayout = (nodes: Node[], edges: Edge[]) => {
  const nodeMap = new Map<string, { x: number; y: number; vx: number; vy: number }>();
  const iterations = 100;
  const repulsion = 800;
  const attraction = 0.005;
  const damping = 0.9;

  // 初始化节点位置，均匀分布在圆周上
  nodes.forEach((node, i) => {
    const angle = (2 * Math.PI * i) / nodes.length;
    nodeMap.set(node.id, {
      x: Math.cos(angle) * 200,
      y: Math.sin(angle) * 200,
      vx: 0,
      vy: 0,
    });
  });

  // 迭代计算力
  for (let iter = 0; iter < iterations; iter++) {
    // 计算节点间的斥力
    nodes.forEach((n1) => {
      const p1 = nodeMap.get(n1.id)!;
      nodes.forEach((n2) => {
        if (n1.id === n2.id) return;
        const p2 = nodeMap.get(n2.id)!;
        const dx = p1.x - p2.x;
        const dy = p1.y - p2.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const force = repulsion / (dist * dist);
        p1.vx += (dx / dist) * force;
        p1.vy += (dy / dist) * force;
      });
    });

    // 计算边连接节点间的引力
    edges.forEach((edge) => {
      const p1 = nodeMap.get(edge.source);
      const p2 = nodeMap.get(edge.target);
      if (!p1 || !p2) return;
      const dx = p2.x - p1.x;
      const dy = p2.y - p1.y;
      p1.vx += dx * attraction;
      p1.vy += dy * attraction;
      p2.vx -= dx * attraction;
      p2.vy -= dy * attraction;
    });

    // 应用阻尼并更新位置
    nodes.forEach((node) => {
      const p = nodeMap.get(node.id)!;
      p.vx *= damping;
      p.vy *= damping;
      p.x += p.vx;
      p.y += p.vy;
    });
  }

  return nodes.map((node) => {
    const p = nodeMap.get(node.id)!;
    return { ...node, position: { x: p.x, y: p.y } };
  });
};

/**
 * 根据布局类型应用相应布局算法
 */
const applyLayout = (nodes: Node[], edges: Edge[], layoutType: LayoutType): Node[] => {
  if (nodes.length === 0) return nodes;
  if (layoutType === 'dagre') {
    return getDagreLayout(nodes, edges);
  }
  return getForceLayout(nodes, edges);
};

/** ER图内部组件，需要ReactFlowProvider包裹 */
const ERDiagramInner: React.FC<IERDiagramProps> = ({ uniqueData }) => {
  const chartRef = useRef<HTMLDivElement>(null);

  // 从Store获取状态和方法
  const {
    erDiagramData,
    loading,
    filterText,
    layoutType,
    includeVirtualFk,
    selectedTableId,
    fetchErDiagram,
    setFilterText,
    setLayoutType,
    setSelectedTableId,
    setIncludeVirtualFk,
  } = useErDiagramStore();

  // ReactFlow节点和边状态
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  // 获取ER图数据
  const fetchData = useCallback(
    () => {
      fetchErDiagram({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        schemaName: uniqueData.schemaName,
        tableNameFilter: filterText || undefined,
        includeVirtualFk,
      });
    },
    [uniqueData, filterText, includeVirtualFk, fetchErDiagram],
  );

  // 数据源变化时重新获取数据
  useEffect(() => {
    fetchData();
  }, [uniqueData.dataSourceId, uniqueData.databaseName, uniqueData.schemaName]);

  // 数据变化时更新节点和边
  useEffect(() => {
    if (!erDiagramData) return;

    // 根据过滤文本筛选节点
    const filteredNodes = filterText
      ? erDiagramData.nodes.filter(
          (n) =>
            n.name.toLowerCase().includes(filterText.toLowerCase()) ||
            (n.comment && n.comment.toLowerCase().includes(filterText.toLowerCase())),
        )
      : erDiagramData.nodes;

    // 根据筛选的节点筛选边
    const filteredNodeIds = new Set(filteredNodes.map((n) => n.id));
    const filteredEdges = erDiagramData.edges.filter(
      (e) => filteredNodeIds.has(e.source) && filteredNodeIds.has(e.target),
    );

    // 计算选中表关联的表ID集合
    const selectedRelatedNodeIds = new Set<string>();
    if (selectedTableId) {
      selectedRelatedNodeIds.add(selectedTableId);
      filteredEdges.forEach((e) => {
        if (e.source === selectedTableId) selectedRelatedNodeIds.add(e.target);
        if (e.target === selectedTableId) selectedRelatedNodeIds.add(e.source);
      });
    }

    // 构建ReactFlow节点
    const rfNodes: Node[] = filteredNodes.map((n) => ({
      id: n.id,
      type: 'tableNode',
      position: { x: 0, y: 0 },
      data: {
        ...n,
        isHighlighted: selectedTableId ? selectedRelatedNodeIds.has(n.id) : false,
        isDimmed: selectedTableId ? !selectedRelatedNodeIds.has(n.id) : false,
      },
    }));

    // 构建ReactFlow边
    const rfEdges: Edge[] = filteredEdges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      type: 'relationEdge',
      data: {
        label: e.label,
        virtual: e.virtual,
        sourceColumn: e.sourceColumn,
        targetColumn: e.targetColumn,
      },
      markerEnd: { type: 'arrowclosed' as const },
      style: e.virtual ? { stroke: '#faad14', strokeWidth: 1.5, strokeDasharray: '5 3' } : { stroke: '#8c8c8c', strokeWidth: 2 },
    }));

    // 应用布局算法
    const laidOutNodes = applyLayout(rfNodes, rfEdges, layoutType);
    setNodes(laidOutNodes);
    setEdges(rfEdges);
  }, [erDiagramData, filterText, layoutType, selectedTableId, setNodes, setEdges]);

  // 点击节点时切换选中状态
  const handleNodeClick: OnNodeClick = useCallback(
    (_event, node) => {
      setSelectedTableId(selectedTableId === node.id ? null : node.id);
    },
    [selectedTableId, setSelectedTableId],
  );

  // 点击画布时取消选中
  const handlePaneClick: OnPaneClick = useCallback(() => {
    setSelectedTableId(null);
  }, [setSelectedTableId]);

  // 刷新数据
  const handleRefresh = useCallback(() => {
    fetchData();
  }, [fetchData]);

  // 切换布局
  const handleLayoutChange = useCallback(
    (type: LayoutType) => {
      setLayoutType(type);
    },
    [setLayoutType],
  );

  // 导出PNG
  const handleExport = useCallback(() => {
    if (!chartRef.current) return;
    toPng(chartRef.current.querySelector('.react-flow') as HTMLElement, {
      backgroundColor: '#fff',
      quality: 1,
    }).then((dataUrl) => {
      const link = document.createElement('a');
      link.download = `er-diagram-${uniqueData.databaseName}.png`;
      link.href = dataUrl;
      link.click();
    });
  }, [uniqueData.databaseName]);

  // MiniMap节点颜色
  const miniMapNodeColor = useCallback((node: Node) => {
    if (selectedTableId) {
      const data = node.data as any;
      if (data?.isHighlighted) return '#1890ff';
      if (data?.isDimmed) return '#f0f0f0';
    }
    return '#91caff';
  }, [selectedTableId]);

  return (
    <div className={styles.erDiagramContainer} ref={chartRef}>
      {/* 表名过滤 */}
      <TableFilter value={filterText} onChange={setFilterText} />
      {/* 工具栏 */}
      <Toolbar
        loading={loading}
        layoutType={layoutType}
        includeVirtualFk={includeVirtualFk}
        onRefresh={handleRefresh}
        onLayoutChange={handleLayoutChange}
        onIncludeVirtualFkChange={setIncludeVirtualFk}
        onExport={handleExport}
      />
      {/* 图例 */}
      <Legend />
      {/* 加载状态 */}
      {loading && !erDiagramData ? (
        <div className={styles.loadingContainer}>
          <Spin size="large" tip={i18n('workspace.erDiagram.loading')} />
        </div>
      ) : !erDiagramData || erDiagramData.nodes.length === 0 ? (
        <div className={styles.emptyContainer}>
          <Empty description={i18n('workspace.erDiagram.noData')} />
        </div>
      ) : (
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={handleNodeClick}
          onPaneClick={handlePaneClick}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          fitViewOptions={{ padding: 0.2 }}
          minZoom={0.1}
          maxZoom={2}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="#f0f0f0" gap={16} />
          <Controls position="bottom-right" showInteractive={false} />
          <MiniMap
            nodeColor={miniMapNodeColor}
            maskColor="rgba(0, 0, 0, 0.1)"
            style={{ background: '#fafafa' }}
            position="bottom-right"
          />
        </ReactFlow>
      )}
    </div>
  );
};

/** ER图组件，包裹ReactFlowProvider */
const ERDiagram: React.FC<IERDiagramProps> = (props) => {
  return (
    <ReactFlowProvider>
      <ERDiagramInner {...props} />
    </ReactFlowProvider>
  );
};

export default ERDiagram;

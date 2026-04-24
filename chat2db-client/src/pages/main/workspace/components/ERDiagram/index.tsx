
import sqlServer from '@/service/sql';
import React, { useCallback, useRef, useState, useMemo } from 'react';
import ReactFlow, {
  ReactFlowProvider,
  addEdge,
  useNodesState,
  useEdgesState,
  useReactFlow,
  Controls,
  MiniMap,
  Background,
  Node,
  Edge,
  Connection,
} from 'reactflow';
import 'reactflow/dist/style.css';
import TableNode from './TableNode';
import Toolbar from './Toolbar';
import {
  ERDiagramData,
  LayoutType,
  TableNode as TableNodeType,
  RelationEdge,
  LegacyErDiagramData,
  TableColumn,
} from './types';
import styles from './index.less';

// 自定义节点类型
const nodeTypes = {
  tableNode: TableNode,
};

interface IERDiagramProps {
  uniqueData: {
    databaseName: string;
    dataSourceId: number;
    schemaName?: string;
    tableName: string;
  };
}

// 布局算法：力导向（使用内置布局）
function circularLayout(tables: TableNodeType[]): Map&lt;string, { x: number; y: number }&gt; {
  const positions = new Map&lt;string, { x: number; y: number }&gt;();
  const centerX = 0;
  const centerY = 0;
  const radius = Math.max(tables.length * 120, 500);
  const angleStep = (2 * Math.PI) / tables.length;

  tables.forEach((table, index) =&gt; {
    const angle = index * angleStep - Math.PI / 2;
    positions.set(table.id, {
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle),
    });
  });

  return positions;
}

function hierarchicalLayout(tables: TableNodeType[], relations: RelationEdge[]): Map&lt;string, { x: number; y: number }&gt; {
  const positions = new Map&lt;string, { x: number; y: number }&gt;();
  const visited = new Set&lt;string&gt;();
  const levels: string[][] = [];

  // 1. 构建入度
  const inDegree = new Map&lt;string, number&gt;();
  tables.forEach((t) =&gt; inDegree.set(t.id, 0));
  relations.forEach((r) =&gt;
    inDegree.set(r.target.tableId, (inDegree.get(r.target.tableId) || 0) + 1)
  );

  // 2. 分层
  const queue: { tableId: string; level: number }[] = [];
  tables
    .filter((t) =&gt; inDegree.get(t.id) === 0)
    .forEach((t) =&gt; queue.push({ tableId: t.id, level: 0 }));

  while (queue.length &gt; 0) {
    const { tableId, level } = queue.shift()!;
    if (visited.has(tableId)) continue;
    visited.add(tableId);

    if (!levels[level]) levels[level] = [];
    levels[level].push(tableId);

    const children = relations
      .filter((r) =&gt; r.source.tableId === tableId)
      .map((r) =&gt; r.target.tableId);
    children.forEach((childId) =&gt; {
      if (!visited.has(childId)) {
        queue.push({ tableId: childId, level: level + 1 });
      }
    });
  }

  // 3. 处理剩余未分层节点
  tables.forEach((t) =&gt; {
    if (!visited.has(t.id)) {
      let added = false;
      for (let i = 0; i &lt; levels.length; i++) {
        const hasRelation = relations.some(
          (r) =&gt;
            (r.source.tableId === t.id &amp;&amp; levels[i].includes(r.target.tableId)) ||
            (r.target.tableId === t.id &amp;&amp; levels[i].includes(r.source.tableId))
        );
        if (hasRelation) {
          if (!levels[i + 1]) levels[i + 1] = [];
          levels[i + 1].push(t.id);
          added = true;
          break;
        }
      }
      if (!added) {
        if (!levels[0]) levels[0] = [];
        levels[0].push(t.id);
      }
    }
  });

  // 4. 分配坐标
  const nodeWidth = 220;
  const nodeHeight = 180;
  const horizontalGap = 120;
  const verticalGap = 180;

  levels.forEach((level, yIndex) =&gt; {
    const totalWidth = level.length * nodeWidth + (level.length - 1) * horizontalGap;
    const startX = -totalWidth / 2;
    level.forEach((tableId, xIndex) =&gt; {
      positions.set(tableId, {
        x: startX + xIndex * (nodeWidth + horizontalGap),
        y: yIndex * (nodeHeight + verticalGap),
      });
    });
  });

  return positions;
}

const ERDiagramContent: React.FC&lt;IERDiagramProps&gt; = ({ uniqueData }) =&gt; {
  const reactFlowWrapper = useRef&lt;HTMLDivElement&gt;(null);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [loading, setLoading] = useState(false);
  const [layout, setLayout] = useState&lt;LayoutType&gt;('FORCE');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showColumns, setShowColumns] = useState(true);
  const [showDataTypes, setShowDataTypes] = useState(true);
  const [showRelations, setShowRelations] = useState(true);
  const [showComments, setShowComments] = useState(true);
  const [erData, setErData] = useState&lt;ERDiagramData | null&gt;(null);

  const { fitView, zoomIn, zoomOut } = useReactFlow();

  // 数据转换
  const transformData = useCallback((data: ERDiagramData | LegacyErDiagramData) =&gt; {
    const newNodes: Node[] = [];
    const newEdges: Edge[] = [];

    // 新数据格式
    if ('tables' in data &amp;&amp; data.tables) {
      const positions =
        data.layout?.positions ||
        (layout === 'CIRCULAR'
          ? circularLayout(data.tables)
          : layout === 'HIERARCHICAL'
          ? hierarchicalLayout(data.tables, data.relations || [])
          : new Map());

      data.tables.forEach((table) =&gt; {
        const pos = positions.get(table.id) || { x: Math.random() * 1000 - 500, y: Math.random() * 1000 - 500 };
        newNodes.push({
          id: table.id,
          type: 'tableNode',
          position: pos,
          data: {
            ...table,
            columns: showColumns ? table.columns : [],
          },
        });
      });

      if (showRelations &amp;&amp; data.relations) {
        data.relations.forEach((rel) =&gt; {
          let label = '';
          switch (rel.type) {
            case 'ONE_TO_ONE':
              label = '1:1';
              break;
            case 'ONE_TO_MANY':
              label = '1:N';
              break;
            case 'MANY_TO_MANY':
              label = 'N:M';
              break;
          }

          newEdges.push({
            id: rel.id,
            source: rel.source.tableId,
            target: rel.target.tableId,
            sourceHandle: rel.source.columnName,
            targetHandle: rel.target.columnName,
            label,
            type: 'smoothstep',
            data: rel,
          });
        });
      }
    }
    // 旧数据格式，向后兼容
    else if ('nodes' in data &amp;&amp; data.nodes) {
      data.nodes.forEach((node) =&gt; {
        newNodes.push({
          id: node.id,
          type: 'tableNode',
          position: { x: Math.random() * 800 - 400, y: Math.random() * 600 - 300 },
          data: {
            id: node.id,
            name: node.name,
            columns: [],
            indexes: [],
          } as TableNodeType,
        });
      });

      if (showRelations &amp;&amp; data.edges) {
        data.edges.forEach((edge) =&gt; {
          newEdges.push({
            id: edge.id,
            source: edge.source,
            target: edge.target,
            label: edge.description,
            type: 'smoothstep',
          });
        });
      }
    }

    return { newNodes, newEdges };
  }, [layout, showColumns, showRelations]);

  const fetchErDiagramData = async (refresh: boolean) =&gt; {
    setLoading(true);
    try {
      const res = await sqlServer.getErDiagram({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        schemaName: uniqueData.schemaName,
        refresh: refresh,
      });
      setErData(res);
      
      const { newNodes, newEdges } = transformData(res);
      setNodes(newNodes);
      setEdges(newEdges);
      
      setTimeout(() =&gt; fitView(), 100);
    } catch (error) {
      console.error('获取 ER 图数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  const onConnect = useCallback(
    (params: Connection) =&gt; setEdges((eds) =&gt; addEdge(params, eds)),
    [setEdges]
  );

  const handleLayoutChange = useCallback((newLayout: LayoutType) =&gt; {
    setLayout(newLayout);
    if (erData) {
      const { newNodes, newEdges } = transformData(erData);
      setNodes(newNodes);
      setEdges(newEdges);
      setTimeout(() =&gt; fitView(), 100);
    }
  }, [erData, transformData, setNodes, setEdges, fitView]);

  const handleExport = useCallback(() =&gt; {
    console.log('导出功能待实现');
  }, []);

  const handleToggleFullscreen = useCallback(() =&gt; {
    setIsFullscreen(!isFullscreen);
  }, [isFullscreen]);

  React.useEffect(() =&gt; {
    fetchErDiagramData(false);
  }, [uniqueData]);

  React.useEffect(() =&gt; {
    if (erData) {
      const { newNodes, newEdges } = transformData(erData);
      setNodes(newNodes);
      setEdges(newEdges);
    }
  }, [showColumns, showRelations, showDataTypes, showComments]);

  return (
    &lt;div
      className={`${styles.erDiagramContainer} ${isFullscreen ? styles.fullscreen : ''}`}
      ref={reactFlowWrapper}
    &gt;
      &lt;Toolbar
        layout={layout}
        onLayoutChange={handleLayoutChange}
        onRefresh={() =&gt; fetchErDiagramData(true)}
        onExport={handleExport}
        onToggleFullscreen={handleToggleFullscreen}
        isFullscreen={isFullscreen}
        showColumns={showColumns}
        onToggleShowColumns={setShowColumns}
        showDataTypes={showDataTypes}
        onToggleShowDataTypes={setShowDataTypes}
        showRelations={showRelations}
        onToggleShowRelations={setShowRelations}
        showComments={showComments}
        onToggleShowComments={setShowComments}
        onZoomIn={zoomIn}
        onZoomOut={zoomOut}
        onFitView={fitView}
      /&gt;
      &lt;div className={styles.reactFlowContainer}&gt;
        &lt;ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.2 }}
        &gt;
          &lt;Background /&gt;
          &lt;Controls /&gt;
          &lt;MiniMap nodeColor="#eee" /&gt;
        &lt;/ReactFlow&gt;
      &lt;/div&gt;
    &lt;/div&gt;
  );
};

const ERDiagram: React.FC&lt;IERDiagramProps&gt; = (props) =&gt; {
  return (
    &lt;ReactFlowProvider&gt;
      &lt;ERDiagramContent {...props} /&gt;
    &lt;/ReactFlowProvider&gt;
  );
};

export default ERDiagram;

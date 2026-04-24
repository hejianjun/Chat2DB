/**
 * ER图自定义节点组件
 * 用于显示数据库表的节点，包含表名、注释和列数量信息
 */
import React, { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { TableOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { IErNode } from '@/service/sql';
import styles from './TableNode.less';

/** 节点数据接口，扩展基础节点数据 */
interface TableNodeData extends IErNode {
  /** 是否高亮显示（选中时） */
  isHighlighted?: boolean;
  /** 是否淡化显示（非选中关联表时） */
  isDimmed?: boolean;
}

const TableNode = memo(({ data }: NodeProps) => {
  const nodeData = data as unknown as TableNodeData;

  return (
    <Tooltip
      title={
        nodeData.comment
          ? `${nodeData.name}: ${nodeData.comment}`
          : nodeData.name
      }
    >
      <div
        className={`${styles.tableNode} ${nodeData.isHighlighted ? styles.highlighted : ''} ${nodeData.isDimmed ? styles.dimmed : ''}`}
      >
        {/* 左侧和顶部输入连接点 */}
        <Handle type="target" position={Position.Left} className={styles.handle} />
        <Handle type="target" position={Position.Top} className={styles.handle} />
        {/* 表头：表图标和表名 */}
        <div className={styles.tableHeader}>
          <TableOutlined className={styles.tableIcon} />
          <span className={styles.tableName}>{nodeData.name}</span>
        </div>
        {/* 表体：注释和列数量 */}
        {(nodeData.comment || nodeData.columnCount != null) && (
          <div className={styles.tableBody}>
            {nodeData.comment && (
              <div className={styles.tableComment}>{nodeData.comment}</div>
            )}
            {nodeData.columnCount != null && (
              <div className={styles.columnCount}>
                {nodeData.columnCount} columns
              </div>
            )}
          </div>
        )}
        {/* 右侧和底部输出连接点 */}
        <Handle type="source" position={Position.Right} className={styles.handle} />
        <Handle type="source" position={Position.Bottom} className={styles.handle} />
      </div>
    </Tooltip>
  );
});

export default TableNode;

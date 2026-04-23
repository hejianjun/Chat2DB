
import React, { useState } from 'react';
import { Handle, Position, NodeProps } from 'reactflow';
import { TableNode as TableNodeType } from './types';
import styles from './index.less';

interface TableNodeData extends TableNodeType {
  onSelect?: () =&gt; void;
  selected?: boolean;
}

const TableNode: React.FC&lt;NodeProps&lt;TableNodeData&gt;&gt; = ({ data, selected }) =&gt; {
  const [isExpanded, setIsExpanded] = useState(true);

  return (
    &lt;div className={`${styles.tableNode} ${selected ? styles.selected : ''}`}&gt;
      {/* 输入连接点 */}
      &lt;Handle type="target" position={Position.Top} className={styles.handle} /&gt;
      
      {/* 表头 */}
      &lt;div className={styles.tableHeader} onClick={() =&gt; setIsExpanded(!isExpanded)}&gt;
        &lt;div className={styles.tableName}&gt;
          &lt;span className={styles.icon}&gt;📊&lt;/span&gt;
          &lt;span&gt;{data.name}&lt;/span&gt;
          {data.comment &amp;&amp; (
            &lt;span className={styles.tableComment}&gt; - {data.comment}&lt;/span&gt;
          )}
        &lt;/div&gt;
        &lt;div className={styles.expandButton}&gt;
          {isExpanded ? '▼' : '▶'}
        &lt;/div&gt;
      &lt;/div&gt;

      {/* 列列表 - 展开时显示 */}
      {isExpanded &amp;&amp; (
        &lt;div className={styles.tableBody}&gt;
          {data.columns?.map((column) =&gt; (
            &lt;div
              key={column.name}
              className={`${styles.tableColumn} ${column.isPrimaryKey ? styles.pk : ''} ${
                column.isForeignKey ? styles.fk : ''
              }`}
            &gt;
              &lt;div className={styles.columnIcons}&gt;
                {column.isPrimaryKey &amp;&amp; &lt;span className={styles.pkIcon}&gt;🔑&lt;/span&gt;}
                {column.isForeignKey &amp;&amp; &lt;span className={styles.fkIcon}&gt;🔗&lt;/span&gt;}
              &lt;/div&gt;
              &lt;div className={styles.columnName}&gt;{column.name}&lt;/div&gt;
              &lt;div className={styles.columnType}&gt;{column.dataType}&lt;/div&gt;
              &lt;div className={styles.columnNullable}&gt;
                {column.nullable ? '⚪' : '⚫'}
              &lt;/div&gt;
            &lt;/div&gt;
          ))}
        &lt;/div&gt;
      )}

      {/* 输出连接点 */}
      &lt;Handle type="source" position={Position.Bottom} className={styles.handle} /&gt;
    &lt;/div&gt;
  );
};

export default TableNode;

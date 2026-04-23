
import React from 'react';
import { Button, Select, ButtonGroup, Checkbox } from 'antd';
import {
  ReloadOutlined,
  DownloadOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  FullscreenOutlined as FitViewOutlined,
} from '@ant-design/icons';
import { LayoutType } from './types';
import styles from './index.less';

const { Option } = Select;

interface ToolbarProps {
  layout: LayoutType;
  onLayoutChange: (layout: LayoutType) =&gt; void;
  onRefresh: () =&gt; void;
  onExport: () =&gt; void;
  onToggleFullscreen: () =&gt; void;
  isFullscreen: boolean;
  showColumns: boolean;
  onToggleShowColumns: (show: boolean) =&gt; void;
  showDataTypes: boolean;
  onToggleShowDataTypes: (show: boolean) =&gt; void;
  showRelations: boolean;
  onToggleShowRelations: (show: boolean) =&gt; void;
  showComments: boolean;
  onToggleShowComments: (show: boolean) =&gt; void;
  onZoomIn: () =&gt; void;
  onZoomOut: () =&gt; void;
  onFitView: () =&gt; void;
}

const Toolbar: React.FC&lt;ToolbarProps&gt; = ({
  layout,
  onLayoutChange,
  onRefresh,
  onExport,
  onToggleFullscreen,
  isFullscreen,
  showColumns,
  onToggleShowColumns,
  showDataTypes,
  onToggleShowDataTypes,
  showRelations,
  onToggleShowRelations,
  showComments,
  onToggleShowComments,
  onZoomIn,
  onZoomOut,
  onFitView,
}) =&gt; {
  return (
    &lt;div className={styles.toolbar}&gt;
      {/* 布局选择 */}
      &lt;Select
        value={layout}
        onChange={onLayoutChange}
        style={{ width: 150, marginRight: 16 }}
      &gt;
        &lt;Option value="FORCE"&gt;力导向布局&lt;/Option&gt;
        &lt;Option value="HIERARCHICAL"&gt;分层布局&lt;/Option&gt;
        &lt;Option value="ORTHOGONAL"&gt;正交布局&lt;/Option&gt;
        &lt;Option value="CIRCULAR"&gt;圆形布局&lt;/Option&gt;
      &lt;/Select&gt;

      {/* 缩放控制 */}
      &lt;ButtonGroup style={{ marginRight: 16 }}&gt;
        &lt;Button icon={&lt;ZoomInOutlined /&gt;} onClick={onZoomIn}&gt;放大&lt;/Button&gt;
        &lt;Button icon={&lt;ZoomOutOutlined /&gt;} onClick={onZoomOut}&gt;缩小&lt;/Button&gt;
        &lt;Button icon={&lt;FitViewOutlined /&gt;} onClick={onFitView}&gt;适应视图&lt;/Button&gt;
      &lt;/ButtonGroup&gt;

      {/* 显示控制 */}
      &lt;Checkbox.Group style={{ marginRight: 16 }}&gt;
        &lt;Checkbox checked={showColumns} onChange={(e) =&gt; onToggleShowColumns(e.target.checked)}&gt;
          显示列
        &lt;/Checkbox&gt;
        &lt;Checkbox checked={showDataTypes} onChange={(e) =&gt; onToggleShowDataTypes(e.target.checked)}&gt;
          显示数据类型
        &lt;/Checkbox&gt;
        &lt;Checkbox checked={showRelations} onChange={(e) =&gt; onToggleShowRelations(e.target.checked)}&gt;
          显示关系
        &lt;/Checkbox&gt;
        &lt;Checkbox checked={showComments} onChange={(e) =&gt; onToggleShowComments(e.target.checked)}&gt;
          显示注释
        &lt;/Checkbox&gt;
      &lt;/Checkbox.Group&gt;

      {/* 操作按钮 */}
      &lt;Button icon={&lt;ReloadOutlined /&gt;} onClick={onRefresh}&gt;刷新&lt;/Button&gt;
      &lt;Button icon={&lt;DownloadOutlined /&gt;} onClick={onExport}&gt;导出&lt;/Button&gt;
      &lt;Button
        icon={isFullscreen ? &lt;FullscreenExitOutlined /&gt; : &lt;FullscreenOutlined /&gt;}
        onClick={onToggleFullscreen}
      &gt;
        {isFullscreen ? '退出全屏' : '全屏'}
      &lt;/Button&gt;
    &lt;/div&gt;
  );
};

export default Toolbar;

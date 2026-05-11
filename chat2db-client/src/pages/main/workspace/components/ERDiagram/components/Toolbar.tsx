/**
 * ER图工具栏组件
 * 提供刷新、布局切换、虚拟外键开关、推断虚拟外键、缩放控制、导出等功能
 */
import React, { memo } from 'react';
import { Button, Space, Switch, Select, Tooltip } from 'antd';
import {
  ReloadOutlined,
  DownloadOutlined,
  ApartmentOutlined,
  ExpandOutlined,
  CompressOutlined,
  BranchesOutlined,
} from '@ant-design/icons';
import { useReactFlow } from '@xyflow/react';
import i18n from '@/i18n';
import { LayoutType } from '../store';
import styles from './Toolbar.less';

interface IToolbarProps {
  loading: boolean;
  layoutType: LayoutType;
  includeVirtualFk: boolean;
  showOnlyRelatedTables: boolean;
  onRefresh: () => void;
  onLayoutChange: (type: LayoutType) => void;
  onIncludeVirtualFkChange: (value: boolean) => void;
  onShowOnlyRelatedTablesChange: (value: boolean) => void;
  onInferVirtualFk: () => void;
  inferring: boolean;
  onExport: () => void;
}

const Toolbar = memo(
  ({
    loading,
    layoutType,
    includeVirtualFk,
    showOnlyRelatedTables,
    onRefresh,
    onLayoutChange,
    onIncludeVirtualFkChange,
    onShowOnlyRelatedTablesChange,
    onInferVirtualFk,
    inferring,
    onExport,
  }: IToolbarProps) => {
    const { fitView, zoomIn, zoomOut } = useReactFlow();

    return (
      <div className={styles.toolbar}>
        <Space size="small">
          {/* 刷新按钮 */}
          <Tooltip title={i18n('workspace.erDiagram.refresh')}>
            <Button
              icon={<ReloadOutlined />}
              onClick={onRefresh}
              loading={loading}
              size="small"
            />
          </Tooltip>

          {/* 推断虚拟外键按钮 */}
          <Tooltip title={i18n('workspace.erDiagram.inferVirtualFk')}>
            <Button
              icon={<BranchesOutlined />}
              onClick={onInferVirtualFk}
              loading={inferring}
              size="small"
            />
          </Tooltip>

          {/* 布局选择器：层级布局或力导向布局 */}
          <Select
            value={layoutType}
            onChange={onLayoutChange}
            size="small"
            className={styles.layoutSelect}
            options={[
              {
                value: 'dagre',
                label: (
                  <span>
                    <ApartmentOutlined /> {i18n('workspace.erDiagram.hierarchical')}
                  </span>
                ),
              },
              {
                value: 'force',
                label: (
                  <span>
                    <ExpandOutlined /> {i18n('workspace.erDiagram.forceLayout')}
                  </span>
                ),
              },
            ]}
          />

          {/* 虚拟外键开关 */}
          <Tooltip title={i18n('workspace.erDiagram.virtualFk')}>
            <Switch
              size="small"
              checked={includeVirtualFk}
              onChange={onIncludeVirtualFkChange}
            />
          </Tooltip>

          {/* 只显示关联表开关 */}
          <Tooltip title={i18n('workspace.erDiagram.showOnlyRelatedTables')}>
            <Switch
              size="small"
              checked={showOnlyRelatedTables}
              onChange={onShowOnlyRelatedTablesChange}
            />
          </Tooltip>

          {/* 放大按钮 */}
          <Tooltip title={i18n('workspace.erDiagram.zoomIn')}>
            <Button
              icon={<ExpandOutlined />}
              onClick={() => zoomIn()}
              size="small"
            />
          </Tooltip>

          {/* 缩小按钮 */}
          <Tooltip title={i18n('workspace.erDiagram.zoomOut')}>
            <Button
              icon={<CompressOutlined />}
              onClick={() => zoomOut()}
              size="small"
            />
          </Tooltip>

          {/* 适应画布按钮 */}
          <Tooltip title={i18n('workspace.erDiagram.fitView')}>
            <Button
              icon={<ApartmentOutlined />}
              onClick={() => fitView({ padding: 0.2 })}
              size="small"
            />
          </Tooltip>

          {/* 导出PNG按钮 */}
          <Tooltip title={i18n('workspace.erDiagram.export')}>
            <Button
              icon={<DownloadOutlined />}
              onClick={onExport}
              size="small"
            />
          </Tooltip>
        </Space>
      </div>
    );
  },
);

export default Toolbar;

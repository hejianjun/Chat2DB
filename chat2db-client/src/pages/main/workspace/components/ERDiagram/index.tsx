import sqlServer from '@/service/sql'; // 确保导入 sqlServer
import { ReloadOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import * as echarts from 'echarts';
import React, { useEffect, useRef, useState } from 'react';
import styles from './index.less';

interface IERDiagramProps {
  uniqueData: {
    databaseName: string;
    dataSourceId: number;
    schemaName?: string;
    tableName: string;
  };
}

const ERDiagram: React.FC<IERDiagramProps> = ({ uniqueData }) => {
  const chartRef = useRef<HTMLDivElement>(null);
  const [erDiagramData, setErDiagramData] = useState<any | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchErDiagramData = async (refresh: boolean) => {
    setLoading(true);
    try {
      const res = await sqlServer.getErDiagram({
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        schemaName: uniqueData.schemaName,
        refresh: refresh
      });
      setErDiagramData(res);
    } catch (error) {
      console.error('获取 ER 图数据失败:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchErDiagramData(false);
  }, [uniqueData]);

  useEffect(() => {
    if (erDiagramData && chartRef.current) {
      const chartInstance = echarts.init(chartRef.current);

      const data = {
        nodes: erDiagramData.nodes || [],
        edges: erDiagramData.edges || [],
      };

      const option = {
        title: {
          text: 'ER Diagram',
          subtext: `Database: ${uniqueData.databaseName}`,
          left: 'center',
        },
        tooltip: {},
        animation: false,
        series: [
          {
            type: 'graph',
            layout: 'force',
            data: data.nodes.map(node => ({
              ...node,
              symbolSize: 50,
            })),
            links: data.edges.map(edge => ({
              ...edge,
              label: {
                show: true,
                formatter: edge.description || '',
                position: 'middle',
              },
            })),
            categories: [
              { name: 'Category1' },
              { name: 'Category2' },
              { name: 'Category3' },
            ],
            roam: true,
            label: {
              show: true,
            },
            force: {
              repulsion: 200,
            },
          },
        ],
      };

      chartInstance.setOption(option);

      return () => {
        chartInstance.dispose();
      };
    }
  }, [erDiagramData, uniqueData]);

  const handleRefresh = () => {
    fetchErDiagramData(true);
  };

  return (
    <div className={styles.erDiagramContainer}>
      <div className={styles.buttonContainer}>
        <Button
          icon={<ReloadOutlined />}
          onClick={handleRefresh}
          loading={loading}
        >
          刷新
        </Button>
      </div>
      <div ref={chartRef} className={styles.chartContainer} style={{ height: 'calc(100vh - 40px)' }} />
    </div>
  );
};

export default ERDiagram;

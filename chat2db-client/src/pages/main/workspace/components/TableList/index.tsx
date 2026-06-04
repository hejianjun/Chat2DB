import React, { memo, useEffect, useState, useRef } from 'react';
import styles from './index.less';
import classnames from 'classnames';

import { useWorkspaceStore } from '@/pages/main/workspace/store';

// ----- components -----
import OperationLine from '../OperationLine';

import Tree from '@/blocks/Tree';
import { treeConfig } from '@/blocks/Tree/treeConfig';
import { ITreeNode } from '@/typings';
import { TreeNodeType } from '@/constants';

interface IProps {
  className?: string;
}

export default memo<IProps>((props) => {
  const { className } = props;
  const [treeData, setTreeData] = useState<ITreeNode[] | null>(null);

  const [searchValue, setSearchValue] = useState<string>('');

  const currentConnectionDetails = useWorkspaceStore((state) => state.currentConnectionDetails);

  const abortControllerRef = useRef<AbortController | null>(null);
  const requestIdRef = useRef(0);

  const getTreeData = (refresh = false) => {
    if (!currentConnectionDetails?.id) {
      setTreeData([]);
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    abortControllerRef.current = new AbortController();
    const signal = abortControllerRef.current.signal;

    const treeNodeType = currentConnectionDetails.supportDatabase ? TreeNodeType.DATA_SOURCE : TreeNodeType.DATABASE;
    setTreeData((prevTreeData) => (refresh && prevTreeData ? prevTreeData : null));
    treeConfig[treeNodeType]
      .getChildren?.(
        {
          dataSourceId: currentConnectionDetails.id,
          dataSourceName: currentConnectionDetails.alias,
          refresh: refresh,
          extraParams: {
            dataSourceId: currentConnectionDetails.id,
            dataSourceName: currentConnectionDetails.alias,
            databaseType: currentConnectionDetails.type,
          },
        },
        { signal },
      )
      .then((res) => {
        if (signal.aborted || requestIdRef.current !== requestId) return;
        setTreeData(res);
      })
      .catch(() => {
        if (signal.aborted || requestIdRef.current !== requestId) return;
        setTreeData([]);
      });
  };

  useEffect(() => {
    getTreeData();
  }, [currentConnectionDetails]);

  useEffect(() => {
    return () => {
      requestIdRef.current += 1;
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  return (
    <div className={classnames(styles.treeContainer, className)}>
      <OperationLine getTreeData={getTreeData} searchValue={searchValue} setSearchValue={setSearchValue} />
      <Tree className={styles.treeBox} searchValue={searchValue} treeData={treeData} refreshRootData={getTreeData} />
    </div>
  );
});

import React, { useState } from 'react';
import mysqlService from '@/service/sql';
import { Button, Checkbox } from 'antd';
import { openModal } from '@/store/common/components';
import styles from './deleteTable.less';
import i18n from '@/i18n';

export const truncateTable = (treeNodeData, loadData) => {
  openModal({
    width: '450px',
    content: <TruncateModalContent treeNodeData={treeNodeData} loadData={loadData} openModal={openModal} />,
  });
};

export const TruncateModalContent = (params: { treeNodeData: any; openModal: any; loadData: any }) => {
  const { treeNodeData, loadData, openModal } = params;
  // 禁用确定按钮
  const [userChecked, setUserChecked] = useState<boolean>(false);

  const onOk = () => {
    const p: any = {
      dataSourceId: treeNodeData.extraParams.dataSourceId,
      databaseName: treeNodeData.extraParams.databaseName,
      schemaName: treeNodeData.extraParams.schemaName,
      tableName: treeNodeData.name,
    };
    mysqlService.truncateTable(p).then(() => {
      loadData({
        refresh: true,
        treeNodeData: treeNodeData.parentNode
      });
      openModal(false);
    })
.catch((error) => {
      console.error('Error truncating table:', error);
    });
  };

  return (
    <div className={styles.deleteModalContent}>
      <div className={styles.title}>{i18n('workspace.tree.truncate.table.tip', `"${treeNodeData.name}"`)}</div>
      <div className={styles.checkContainer}>
        <Checkbox
          value={userChecked}
          onChange={(e) => {
            setUserChecked(e.target.checked);
          }}
        >
          {i18n('workspace.tree.truncate.tip')}
        </Checkbox>
      </div>
      <div className={styles.deleteTableFooter}>
        <Button
          type="primary"
          onClick={() => {
            openModal(false);
          }}
        >
          {i18n('common.button.cancel')}
        </Button>
        <Button disabled={!userChecked} onClick={onOk}>
          {i18n('common.button.affirm')}
        </Button>
      </div>
    </div>
  );
};

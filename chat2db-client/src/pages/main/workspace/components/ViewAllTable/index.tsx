import React, { memo, useEffect, useState } from 'react';
import i18n from '@/i18n';
import styles from './index.less';
import classnames from 'classnames';
import { Table, Dropdown, Input, Pagination, Button, Form, Modal, message } from 'antd';
import { DatabaseTypeCode, TreeNodeType, OperationColumn, WorkspaceTabType } from '@/constants';
import sqlServer, { IBatchModifyTableSqlParams } from '@/service/sql';
import type { ColumnsType } from 'antd/es/table';
import { IPageParams } from '@/typings';
import { v4 as uuid } from 'uuid';
import ExecuteSQL from '@/components/ExecuteSQL';

// ----- components -----
import Iconfont from '@/components/Iconfont';
import { getRightClickMenu } from '@/blocks/Tree/hooks/useGetRightClickMenu';
import MenuLabel from '@/components/MenuLabel';
import { setCurrentWorkspaceGlobalExtend } from '@/pages/main/workspace/store/common';

// ----- store -----
import { addWorkspaceTab } from '@/pages/main/workspace/store/console';

const { Search } = Input;

interface IProps {
  className?: string;
  uniqueData: {
    dataSourceId: string;
    dataSourceName: string;
    databaseType: DatabaseTypeCode;
    databaseName?: string;
    schemaName?: string;
  };
}

export default memo<IProps>((props) => {
  const { className, uniqueData, } = props;
  const [tableData, setTableData] = useState<any[] | null>(null);
  const [tableLoading, setTableLoading] = useState<boolean>(false);
  const tableBoxRef = React.useRef<HTMLDivElement>(null);
  const [allTableWidth, setAllTableWidth] = useState(0);
  const [allTableHeight, setAllTableHeight] = useState(0);
  const [activeId, setActiveId] = useState<string>('');
  const [tableDataTotal, setTableDataTotal] = useState(0);
  const [currentPageNo, setCurrentPageNo] = useState(1);
  const [openDropdown, setOpenDropdown] = useState<boolean | undefined>(undefined);
  const [dropdownItems, setDropdownItems] = useState<any[]>([]);
  const [isEditing, setIsEditing] = useState<boolean>(false);
  const [viewSqlModal, setViewSqlModal] = useState<boolean>(false);
  const [appendValue, setAppendValue] = useState<string>('');
  const [form] = Form.useForm();

  useEffect(() => {
    getTable({
      pageNo: 1,
      pageSize: 1000,
    });
  }, []);

  useEffect(() => {
    if (openDropdown === false) {
      setOpenDropdown(undefined);
    }
  }, [openDropdown]);

  const getTable = (params: IPageParams) => {
    setCurrentPageNo(params.pageNo);
    setTableLoading(true);
    sqlServer
      .getTableList({
        ...props.uniqueData,
        ...(params || {}),
      } as any)
      .then((res) => {
        setTableDataTotal(res.total);
        const data = res.data.map((t) => {
          const key = uuid();
          return {
            uuid: key,
            name: t.name,
            treeNodeType: TreeNodeType.TABLE,
            key: t.name,
            pinned: t.pinned,
            comment: t.comment,
            extraParams: {
              ...uniqueData,
              tableName: t.name,
            },
          };
        });
        setTableData(data);
      })
      .finally(() => {
        setTableLoading(false);
      });
  };

  const paginationChange = (pageNo: number) => {
    getTable({
      pageNo,
      pageSize: 1000,
    });
  };

  const createTable = () => {
    addWorkspaceTab({
      id: uuid(),
      title: i18n('editTable.button.createTable'),
      type: WorkspaceTabType.CreateTable,
      uniqueData: {
        ...props.uniqueData,
      },
    });
  };

  const getDropdownsItems = (record) => {
    const rightClickMenu = getRightClickMenu({
      treeNodeData: record,
      loadData: () => { },
    });
    const dropdownsItems: any = rightClickMenu.map((item) => {
      return {
        key: item.key,
        type: item.type,
        onClick: () => {
          setOpenDropdown(false);
          item.onClick(record);
        },
        label: <MenuLabel icon={item.labelProps.icon} label={item.labelProps.label} />,
      };
    });

    const excludeList = [
      OperationColumn.OpenTable,
      OperationColumn.CreateConsole,
      // OperationColumn.Pin,
      OperationColumn.ViewDDL,
      OperationColumn.EditTable,
      OperationColumn.CopyName,
    ];

    return dropdownsItems.filter((item) => excludeList.includes(item.type));
  };

  const renderCell = (text, record, dataIndex) => {
    if (dataIndex === 'name') {
      return (
        <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeId === record.key })}>
          {text}
        </div>
      );
    }

    return isEditing ? (
      <Form.Item
        name={[record.key, dataIndex]}
        style={{ margin: 0 }}
        rules={dataIndex === 'comment' ? [] : [{ required: true, message: `${dataIndex} is required.` }]} // 去掉 Comment 的必填规则
      >
        <Input />
      </Form.Item>
    ) : (
      <div className={classnames(styles.tableCell, { [styles.activeTableCell]: activeId === record.key })}>{text}</div>
    );
  };

  const columns: ColumnsType<any> = [
    {
      title: 'Table name',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => renderCell(text, record, 'name'),
    },
    {
      title: 'Comment',
      dataIndex: 'comment',
      key: 'comment',
      render: (text, record) => renderCell(text, record, 'comment'),
    },
  ];

  const startEditing = () => {
    form.setFieldsValue(
      tableData?.reduce((acc, record) => {
        acc[record.key] = record;
        return acc;
      }, {})
    );
    setIsEditing(true);
  };

  const cancelEditing = () => {
    setIsEditing(false);
  };

  const saveAll = async () => {
    try {
      if (tableData && uniqueData.databaseName) {
        const values = await form.validateFields();
        const newData = tableData.map((item) => ({
          ...item,
          ...values[item.key],
        }));
        setIsEditing(false);
        const params: IBatchModifyTableSqlParams = {
          databaseName: uniqueData.databaseName,
          dataSourceId: uniqueData.dataSourceId,
          schemaName: uniqueData.schemaName,
          refresh: true,
          oldTables: tableData,
          newTables: newData,
        };
        // 调用批量获取修改表的SQL语句的API
        sqlServer.getBatchModifyTableSql(params).then(res => {
          setViewSqlModal(true); // 显示SQL预览Modal
          setAppendValue(res?.join('\n'));
        })
      }

    } catch (errInfo) {
      console.log('Validate Failed:', errInfo);
    }
  };
  const executeSuccessCallBack = () => {
    setViewSqlModal(false);
    message.success(i18n('common.text.successfulExecution'));
    // 保存成功后，刷新左侧树
    getTable({
      refresh: true,
      pageNo: currentPageNo,
      pageSize: 1000,
    });
  };

  // 监听allTable的高度的变化
  useEffect(() => {
    const resizeObserver = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      setAllTableWidth(width);
      setAllTableHeight(height);
    });
    resizeObserver.observe(tableBoxRef.current!);
  }, []);

  // 监听allTable的宽度的变化
  useEffect(() => {
    const resizeObserver = new ResizeObserver((entries) => {
      const { width, height } = entries[0].contentRect;
      setAllTableWidth(width);
      setAllTableHeight(height);
    });
    resizeObserver.observe(tableBoxRef.current!);
  }, []);

  const onSearch = (value: string) => {
    getTable({
      pageNo: 1,
      pageSize: 1000,
      searchKey: value,
    });
  };

  return (
    <div className={classnames(styles.allTable, className)}>
      <div className={styles.headerBox}>
        <div className={styles.headerBoxLeft}>
          <Iconfont code="&#xe726;" box boxSize={24} onClick={createTable} />
          <Iconfont
            onClick={() => {
              getTable({
                pageNo: 1,
                pageSize: 1000,
                refresh: true,
              });
            }}
            code="&#xe668;"
            box
            boxSize={24}
          />
        </div>
        <div className={styles.headerBoxRight}>
          <Search size="small" placeholder={i18n('common.text.search')} onSearch={onSearch} style={{ width: 150 }} />
          {isEditing ? (
            <>
              <Button onClick={saveAll} style={{ marginLeft: 8 }}>
                {i18n('common.button.saveAll')}
              </Button>
              <Button onClick={cancelEditing} style={{ marginLeft: 8 }}>
                {i18n('common.button.cancel')}
              </Button>
            </>
          ) : (
            <Button onClick={startEditing} style={{ marginLeft: 8 }}>
              {i18n('common.button.editAll')}
            </Button>
          )}
        </div>
      </div>
      <div className={styles.contentCenter}>
        <Dropdown
          open={openDropdown}
          menu={{
            items: dropdownItems,
          }}
          trigger={['contextMenu']}
          onOpenChange={(_open) => {
            setOpenDropdown(_open);
          }}
        >
          <div ref={tableBoxRef} className={styles.tableBox}>
            <Form form={form} component={false}>
              <Table
                loading={tableLoading}
                onRow={(row) => {
                  return {
                    onClick: () => {
                      setActiveId(row.key);
                      setCurrentWorkspaceGlobalExtend({
                        code: 'viewDDL',
                        uniqueData: {
                          ...uniqueData,
                          tableName: row.name,
                        }
                      });
                    },
                    onContextMenu: (event) => {
                      event.preventDefault();
                      setActiveId(row.key);
                      setOpenDropdown(true);
                      setDropdownItems(getDropdownsItems(tableData?.find((t) => t.key === row.key)));
                    },
                  };
                }}
                virtual
                scroll={{ x: allTableWidth - 10, y: allTableHeight - 25 }}
                columns={columns}
                pagination={false}
                dataSource={tableData || []}
              />
            </Form>
          </div>
        </Dropdown>
      </div>
      <div className={styles.pagingBox}>
        <Pagination
          onChange={paginationChange}
          showSizeChanger={false}
          current={currentPageNo}
          pageSize={1000}
          total={tableDataTotal}
        />
      </div>
      <Modal
        title={i18n('editTable.title.sqlPreview')}
        open={!!viewSqlModal} // 控制Modal显示
        onCancel={() => setViewSqlModal(false)} // 关闭Modal
        width="60vw"
        maskClosable={false}
        footer={false}
        destroyOnHidden={true}
      >
        <ExecuteSQL
          initSql={appendValue}
          databaseName={uniqueData.databaseName}
          dataSourceId={uniqueData.dataSourceId}
          schemaName={uniqueData.schemaName}
          databaseType={uniqueData.databaseType}
          executeSuccessCallBack={executeSuccessCallBack}
        />
      </Modal>
    </div>
  );
});

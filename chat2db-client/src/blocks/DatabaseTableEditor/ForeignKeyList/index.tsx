import React, { useContext, useEffect, useState, useRef, forwardRef, ForwardedRef, useImperativeHandle } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import { MenuOutlined } from '@ant-design/icons';
import { DndContext, type DragEndEvent } from '@dnd-kit/core';
import { restrictToVerticalAxis } from '@dnd-kit/modifiers';
import { Table, Input, Form, Select, Checkbox } from 'antd';
import { v4 as uuidv4 } from 'uuid';
import { arrayMove, SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Context } from '../index';
import { IForeignKeyItemNew, IForeignKey } from '@/typings'; // 假设你有一个外键的类型定义
import i18n from '@/i18n';
import { EditColumnOperationType } from '@/constants';
import Iconfont from '@/components/Iconfont';

interface RowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  'data-row-key': string;
}

interface IProps { }

// 编辑配置
interface IEditingConfig extends IForeignKey {
  editKey: string;
}

export type IForeignKeyListInfo = IForeignKeyItemNew[]; // 外键信息列表类型

export interface IForeignKeyListRef {
  getForeignKeyListInfo: () => IForeignKeyListInfo; // 获取外键信息列表的方法
}

// 创建一个空的外键数据结构
const createInitialData = () => {
  return {
    key: uuidv4(),
    name: null,
    tableName: null,
    schemaName: null,
    databaseName: null,
    column: null,
    referencedTable: null,
    referencedColumn: null,
    updateRule: 0,
    deleteRule: 0,
    comment: null,
    editStatus: EditColumnOperationType.Add,
  };
};

const Row = ({ children, ...props }: RowProps) => {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({
    id: props['data-row-key'],
  });

  const style: React.CSSProperties = {
    ...props.style,
    transform: CSS.Transform.toString(transform && { ...transform, scaleY: 1 }),
    transition,
    ...(isDragging ? { position: 'relative', zIndex: 9999 } : {}),
  };

  return (
    <tr {...props} ref={setNodeRef} style={style} {...attributes}>
      {React.Children.map(children, (child) => {
        if ((child as React.ReactElement).key === 'sort') {
          return React.cloneElement(child as React.ReactElement, {
            children: (
              <MenuOutlined ref={setActivatorNodeRef} style={{ touchAction: 'none', cursor: 'move' }} {...listeners} />
            ),
          });
        }
        return child;
      })}
    </tr>
  );
};


const ForeignKeyList = forwardRef((props: IProps, ref: ForwardedRef<any>) => {
  const { tableDetails } = useContext(Context);
  const [dataSource, setDataSource] = useState<IForeignKeyItemNew[]>([createInitialData()]);
  const [form] = Form.useForm();
  const [editingData, setEditingData] = useState<IForeignKeyItemNew | null>(null);
  const [editingConfig, setEditingConfig] = useState<IEditingConfig | null>(null);
  const tableRef = useRef<HTMLDivElement>(null);

  const isEditing = (record: IForeignKeyItemNew) => record.key === editingData?.key;
  const handleFieldsChange = (field: any) => {
    let { value } = field[0];
    const { name: nameList } = field[0];
    const name = nameList[0];
    const newData = dataSource.map((item) => {
      if (item.key === editingData?.key) {
        // 判断当前数据是新增的数据还是编辑后的数据
        let editStatus = item.editStatus;
        if (editStatus !== EditColumnOperationType.Add) {
          editStatus = EditColumnOperationType.Modify;
        }
        const editingDataItem = {
          ...item,
          [name]: value,
          editStatus,
        };
        return editingDataItem;
      }
      return item;
    });
    setDataSource(newData);
  };

  const edit = (record: IForeignKeyItemNew) => {
    if (record.key) {
      form.setFieldsValue({ ...record });
      setEditingData(record);
    }
  };

  // 整理服务端返回的数据，构造为前端需要的数据结构
  useEffect(() => {
    if (tableDetails) {
      const list =
        tableDetails?.foreignKeyList?.map((t) => {
          return {
            ...t,
            key: uuidv4(),
          };
        }) || [];
      setDataSource(list);
    }
  }, [tableDetails]);

  const columns = [
    {
      key: 'sort',
      width: '40px',
      align: 'center',
      fixed: 'left',
    },
    {
      title: i18n('editTable.label.foreignKeyName'),
      dataIndex: 'name',
      width: '160px',
      fixed: 'left',
      render: (text: string, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return (
          <div className={styles.cellContent}>
            {editable ? (
              <Form.Item name="name" style={{ margin: 0 }}>
                <Input autoComplete="off" />
              </Form.Item>
            ) : (
              <div className={styles.editableCell}>{text}</div>
            )}
          </div>
        );
      },
    },
    {
      title: i18n('editTable.label.column'),
      dataIndex: 'column',
      width: '160px',
      render: (text: string, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return (
          <div className={styles.cellContent}>
            {editable ? (
              <Form.Item name="column" style={{ margin: 0 }}>
                <Input autoComplete="off" />
              </Form.Item>
            ) : (
              <div className={styles.editableCell}>{text}</div>
            )}
          </div>
        );
      },
    },
    {
      title: i18n('editTable.label.referencedTable'),
      dataIndex: 'referencedTable',
      width: '160px',
      render: (text: string, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return (
          <div className={styles.cellContent}>
            {editable ? (
              <Form.Item name="referencedTable" style={{ margin: 0 }}>
                <Input autoComplete="off" />
              </Form.Item>
            ) : (
              <div className={styles.editableCell}>{text}</div>
            )}
          </div>
        );
      },
    },
    {
      title: i18n('editTable.label.referencedColumn'),
      dataIndex: 'referencedColumn',
      width: '160px',
      render: (text: string, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return (
          <div className={styles.cellContent}>
            {editable ? (
              <Form.Item name="referencedColumn" style={{ margin: 0 }}>
                <Input autoComplete="off" />
              </Form.Item>
            ) : (
              <div className={styles.editableCell}>{text}</div>
            )}
          </div>
        );
      },
    },
    {
      title: i18n('editTable.label.updateRule'),
      dataIndex: 'updateRule',
      width: '160px',
      render: (text: number, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return (
          <div className={styles.cellContent}>
            {editable ? (
              <Form.Item name="updateRule" style={{ margin: 0 }}>
                <Select style={{ width: '100%' }}>
                  <Select.Option key={0} value={0}>CASCADE</Select.Option>
                  <Select.Option key={1} value={1}>SET NULL</Select.Option>
                  <Select.Option key={2} value={2}>NO ACTION</Select.Option>
                  <Select.Option key={3} value={3}>RESTRICT</Select.Option>
                  <Select.Option key={4} value={4}>SET DEFAULT</Select.Option>
                </Select>
              </Form.Item>
            ) : (
              <div className={styles.editableCell}>
                {text === 0 ? 'CASCADE' :
                  text === 1 ? 'SET NULL' :
                    text === 2 ? 'NO ACTION' :
                      text === 3 ? 'RESTRICT' :
                        text === 4 ? 'SET DEFAULT' : 'UNKNOWN'}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: i18n('editTable.label.deleteRule'),
      dataIndex: 'deleteRule',
      width: '160px',
      render: (text: number, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return (
          <div className={styles.cellContent}>
            {editable ? (
              <Form.Item name="deleteRule" style={{ margin: 0 }}>
                <Select style={{ width: '100%' }}>
                  <Select.Option key={0} value={0}>CASCADE</Select.Option>
                  <Select.Option key={1} value={1}>SET NULL</Select.Option>
                  <Select.Option key={2} value={2}>NO ACTION</Select.Option>
                  <Select.Option key={3} value={3}>RESTRICT</Select.Option>
                  <Select.Option key={4} value={4}>SET DEFAULT</Select.Option>
                </Select>
              </Form.Item>
            ) : (
              <div className={styles.editableCell}>
                {text === 0 ? 'CASCADE' :
                  text === 1 ? 'SET NULL' :
                    text === 2 ? 'NO ACTION' :
                      text === 3 ? 'RESTRICT' :
                        text === 4 ? 'SET DEFAULT' : 'UNKNOWN'}
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: i18n('editTable.label.comment'),
      dataIndex: 'comment',
      render: (text: string, record: IForeignKeyItemNew) => {
        const editable = isEditing(record);
        return editable ? (
          <Form.Item name="comment" style={{ margin: 0 }}>
            <Input autoComplete="off" />
          </Form.Item>
        ) : (
          <div className={styles.editableCell}>{text}</div>
        );
      },
    },
    {
      width: '40px',
      render: (text: string, record: IForeignKeyItemNew) => {
        return (
          <div
            className={styles.operationBar}
            onClick={() => {
              deleteData(record);
            }}
          >
            <div className={styles.deleteIconBox}>
              <Iconfont code="&#xe64e;" />
            </div>
          </div>
        );
      },
    },
  ];

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (active.id !== over?.id) {
      setDataSource((previous) => {
        const activeIndex = previous.findIndex((i) => i.key === active.id);
        const overIndex = previous.findIndex((i) => i.key === over?.id);
        return arrayMove(previous, activeIndex, overIndex);
      });
    }
  };

  const addData = () => {
    const newData = {
      ...createInitialData(),
    };
    setDataSource([...dataSource, newData]);
    edit(newData);
    setTimeout(() => {
      tableRef.current?.scrollTo(0, tableRef.current?.scrollHeight + 100);
    }, 0);
  };

  const deleteData = (record) => {
    let list: any = [];
    if (record?.editStatus === EditColumnOperationType.Add) {
      list = dataSource.filter((i) => i.key !== record?.key);
    } else {
      list = dataSource.map((i) => {
        if (i.key === record?.key) {
          setEditingData(null);
          setEditingConfig(null);
          return {
            ...i,
            editStatus: EditColumnOperationType.Delete,
          };
        }
        return i;
      });
    }
    setDataSource(list);
  };

  useImperativeHandle(ref, () => ({
    getForeignKeyListInfo: () => {
      return dataSource.map((i) => {
        const data = {
          ...i,
        };
        delete data.key;
        return data;
      });
    },
  }));

  return (
    <div className={styles.foreignKeyList}>
      <Form className={styles.formBox} form={form} onFieldsChange={handleFieldsChange}>
        <div className={styles.tableBox}>
          <DndContext modifiers={[restrictToVerticalAxis]} onDragEnd={onDragEnd}>
            <SortableContext items={dataSource.map((i) => i.key!)} strategy={verticalListSortingStrategy}>
              <Table ref={tableRef}
                components={{
                  body: {
                    row: Row,
                  },
                }}
                style={{
                  maxHeight: '100%',
                  overflow: 'auto',
                }}
                sticky
                onRow={(record) => ({
                  onClick: () => {
                    edit(record);
                  },
                })}
                pagination={false}
                rowKey="key"
                columns={columns as any}
                scroll={{ x: '100%' }}
                dataSource={dataSource.filter((i) => i.editStatus !== EditColumnOperationType.Delete)}
              />
            </SortableContext>
          </DndContext>
          <div onClick={addData} className={styles.addColumnButton}>
            <Iconfont code="&#xe631;" />
            {i18n('editTable.button.addForeignKey')}
          </div>
        </div>
      </Form>
    </div>
  );
});

export default ForeignKeyList;
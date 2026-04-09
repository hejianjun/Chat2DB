import Iconfont from '@/components/Iconfont';
import i18n from '@/i18n/';
import { AIType } from '@/typings/ai';
import { Button, Input, Popover, Radio, Select, Space } from 'antd';
import React, { useState } from 'react';
import styles from './index.less';

export const enum SyncModelType {
  AUTO = 0,
  MANUAL = 1,
}

interface IProps {
  value?: string;
  result?: string;
  tables?: string[];
  syncTableModel: number;
  selectedTables?: string[];
  aiType: AIType;
  disabled?: boolean;
  isStream?: boolean;
  onPressEnter: (value: string) => void;
  onSelectTableSyncModel: (model: number) => void;
  onSelectTables?: (tables: string[]) => void;
  onCancelStream: () => void;
}

const ChatInput = (props: IProps) => {
  const [value, setValue] = useState(props.value);

  const onPressEnter = (e: any) => {
    if (!e.target.value) {
      return;
    }
    if (e.nativeEvent.isComposing && e.key === 'Enter') {
      e.preventDefault();
      return;
    }
    props.onPressEnter?.(e.target.value);
  };

  const renderSelectTable = () => {
    const { onSelectTableSyncModel, syncTableModel } = props;
    return (
      <div className={styles.aiSelectedTable}>
        <Radio.Group
          onChange={(v) => onSelectTableSyncModel(v.target.value)}
          value={syncTableModel}
          style={{ marginBottom: '8px' }}
        >
          <Space direction="horizontal">
            <Radio value={SyncModelType.AUTO}>自动</Radio>
            <Radio value={SyncModelType.MANUAL}>手动</Radio>
          </Space>
        </Radio.Group>
      </div>
    );
  };

  const renderSuffix = () => {
    return (
      <div className={styles.suffixBlock}>
        {props.isStream ? (
          <Iconfont
            onClick={props.onCancelStream}
            code="&#xe652;"
            className={styles.stop}
          />
        ) : (
          <Button
            type="primary"
            className={styles.enter}
            onClick={() => value && props.onPressEnter?.(value)}
          >
            <Iconfont code="&#xe643;" className={styles.enterIcon} />
          </Button>
        )}
        <div className={styles.tableSelectBlock}>
          <Popover content={renderSelectTable()} placement="bottomLeft">
            <Iconfont code="&#xe618;" />
          </Popover>
        </div>
      </div>
    );
  };

  const { tables, selectedTables, onSelectTables } = props;
  const options = (tables || []).map((t) => ({ value: t, label: t }));

  return (
    <div className={styles.chatWrapper}>
      <Select
        className={styles.tableSelect}
        showSearch
        mode="multiple"
        allowClear
        options={options}
        placeholder={i18n('chat.input.tableSelect.placeholder')}
        value={selectedTables}
        onChange={(v) => onSelectTables?.(v)}
      />
      <Input
        disabled={props.disabled}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        bordered={false}
        placeholder={i18n('workspace.ai.input.placeholder')}
        onPressEnter={onPressEnter}
        suffix={renderSuffix()}
      />
    </div>
  );
};

export default ChatInput;

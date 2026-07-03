import React, { memo, useRef } from 'react';
import { Modal } from 'antd';
import MonacoEditor, { IExportRefFunction } from '@/components/MonacoEditor';

interface IMonacoEditorModalProps {
  title?: string;
  open: boolean;
  value?: string;
  onCancel: () => void;
}

const MonacoEditorModal = memo<IMonacoEditorModalProps>(({ title = 'DDL', open, value = '', onCancel }) => {
  const monacoEditorRef = useRef<IExportRefFunction>(null);

  return (
    <Modal
      title={title}
      open={open}
      width="650px"
      onCancel={onCancel}
      footer={false}
    >
      <MonacoEditor id="edit-dialog" ref={monacoEditorRef} defaultValue={value} />
    </Modal>
  );
});

export default MonacoEditorModal;

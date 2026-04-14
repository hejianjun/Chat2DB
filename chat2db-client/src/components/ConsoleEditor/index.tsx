import Popularize from '@/components/Popularize';
import i18n from '@/i18n';
import { IBoundInfo } from '@/typings';
import { Modal, Spin } from 'antd';
import   React,{
  createContext,
  ForwardedRef,
  forwardRef,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import { v4 as uuidv4 } from 'uuid';
import MonacoEditor, { IEditorOptions, IExportRefFunction, IRangeType } from '../MonacoEditor';
import OperationLine from './components/OperationLine';
import styles from './index.less';

// ----- hooks -----
import { useSaveEditorData } from './hooks/useSaveEditorData';

// ----- store -----
import { setCurrentWorkspaceExtend, setPendingAiChat, IAiChatPromptType } from '@/pages/main/workspace/store/common';

// ----- function -----
import { handelCreateConsole } from '@/pages/main/workspace/functions/shortcutKeyCreateConsole';


export type IAppendValue = {
  text: any;
  range?: IRangeType;
};

interface IProps {
  /** 调用来源 */
  source?: 'workspace';
  isActive: boolean;
  /** 添加或修改的内容 */
  appendValue?: IAppendValue;
  defaultValue?: string;
  /** 是否可以开启SQL转到自然语言的相关ai操作 */
  hasAi2Lang?: boolean;
  /** 是否有 */
  hasSaveBtn?: boolean;
  value?: string;
  boundInfo: IBoundInfo;
  setBoundInfo: (params: IBoundInfo) => void;
  editorOptions?: IEditorOptions;
  onExecuteSQL: (sql: string) => void;
}

export interface IConsoleRef {
  editorRef: IExportRefFunction | undefined;
}

interface IIntelligentEditorContext {
  isActive: boolean;
  tableNameList: string[];
  setTableNameList: (tables: string[]) => void;
}

export const IntelligentEditorContext = createContext<IIntelligentEditorContext>({} as any);

function ConsoleEditor(props: IProps, ref: ForwardedRef<IConsoleRef>) {
  const {
    boundInfo,
    setBoundInfo,
    appendValue,
    hasSaveBtn = true,
    source,
    defaultValue,
    isActive,
  } = props;
  const uid = useMemo(() => uuidv4(), []);
  const editorRef = useRef<IExportRefFunction>();
  const [tableNameList, setTableNameList] = useState<string[]>([]);
  const [isLoading] = useState(false);
  const [popularizeModal, setPopularizeModal] = useState(false);
  const [modalProps] = useState({});
  const aiFetchIntervalRef = useRef<any>();

  // ---------------- new-code ----------------
  const { saveConsole } = useSaveEditorData({
    editorRef,
    isActive,
    boundInfo: props.boundInfo,
    source,
    defaultValue,
  });
  // ---------------- new-code ----------------





  useEffect(() => {
    if (appendValue) {
      editorRef?.current?.setValue(appendValue.text, appendValue.range);
    }
  }, [appendValue]);

  useImperativeHandle(
    ref,
    () => ({
      editorRef: editorRef?.current,
    }),
    [editorRef?.current],
  );

  const executeSQL = (sql?: string) => {
    const sqlContent = sql || editorRef?.current?.getCurrentSelectContent() || editorRef?.current?.getAllContent();

    if (!sqlContent) {
      return;
    }
    props.onExecuteSQL && props.onExecuteSQL(sqlContent);
  };

  // 打开 AI 聊天面板并发送选中内容
  const openAiChatWithMessage = (selectedText: string, promptType: IAiChatPromptType) => {
    // 打开 AI 扩展面板
    setCurrentWorkspaceExtend('ai');
    // 设置待发送的 AI 聊天消息
    setPendingAiChat({
      dataSourceId: boundInfo.dataSourceId,
      message: selectedText,
      promptType,
    });
  };

  const addAction = [
    {
      id: 'explainSQL',
      label: i18n('common.text.explainSQL'),
      action: (selectedText: string) => {
        openAiChatWithMessage(selectedText, 'SQL_EXPLAIN');
      },
    },
    {
      id: 'optimizeSQL',
      label: i18n('common.text.optimizeSQL'),
      action: (selectedText: string) => {
        openAiChatWithMessage(selectedText, 'SQL_OPTIMIZER');
      },
    },
    {
      id: 'changeSQL',
      label: i18n('common.text.conversionSQL'),
      action: (selectedText: string) => {
        openAiChatWithMessage(selectedText, 'SQL_2_SQL');
      },
    },
  ];



  // 注册快捷键
  const registerShortcutKey = (editor, monaco) => {
    // 保存
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      const value = editor?.getValue();
      saveConsole(value || '');
    });

    // 执行
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyR, () => {
      const value = editorRef.current?.getCurrentSelectContent();
      executeSQL(value);
    });

    // 执行
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KeyL, () => {
      handelCreateConsole();
    });
  };

  return (
    <IntelligentEditorContext.Provider
      value={{
        isActive,
        tableNameList,
        setTableNameList,
      }}
    >
      <div className={styles.console} ref={ref as any}>
        <OperationLine
          boundInfo={boundInfo}
          saveConsole={saveConsole}
          executeSQL={executeSQL}
          editorRef={editorRef}
          setBoundInfo={setBoundInfo}
          hasSaveBtn={hasSaveBtn}
        />
        <Spin spinning={isLoading} style={{ height: '100%' }}>
          <MonacoEditor
            id={uid}
            defaultValue={defaultValue}
            ref={editorRef as any}
            className={styles.consoleEditor}
            addAction={addAction}
            options={props.editorOptions}
            shortcutKey={registerShortcutKey}
            boundInfo={boundInfo}
          />
        </Spin>
        <Modal
          open={popularizeModal}
          footer={false}
          onCancel={() => {
            aiFetchIntervalRef.current && clearInterval(aiFetchIntervalRef.current);
            setPopularizeModal(false);
          }}
        >
          <Popularize {...modalProps} />
        </Modal>
        
      </div>
    </IntelligentEditorContext.Provider>
  );
}

export default forwardRef(ConsoleEditor);

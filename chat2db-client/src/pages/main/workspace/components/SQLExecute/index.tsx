import React, { memo, useCallback, useEffect, useRef, useState } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import DraggableContainer from '@/components/DraggableContainer';
import ConsoleEditor, { IConsoleRef } from '@/components/ConsoleEditor';
import SearchResult, { ISearchResultRef } from '@/components/SearchResult';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import { IBoundInfo, IManageResultData } from '@/typings';
import { updateConsoleBoundInfo } from '@/pages/main/workspace/store/console';
import { registerConsoleEditor, unregisterConsoleEditor } from './consoleEditorRegistry';
import { registerSearchResult, unregisterSearchResult } from './searchResultRegistry';

interface IProps {
  boundInfo: IBoundInfo;
  initDDL: string;
  consoleTitle?: string;
  // 异步加载 sql
  loadSQL: () => Promise<string>;
  initialResult?: IManageResultData;
}

const SQLExecute = memo<IProps>((props) => {
  const { boundInfo: _boundInfo, initDDL, loadSQL } = props;
  const draggableRef = useRef<any>();
  const searchResultRef = useRef<ISearchResultRef>(null);
  const consoleRef = useRef<IConsoleRef>(null);
  const [boundInfo, setBoundInfoState] = useState<IBoundInfo>(_boundInfo);
  const activeConsoleId = useWorkspaceStore((state) => state.activeConsoleId);

  const setBoundInfo = useCallback((nextBoundInfo: IBoundInfo) => {
    setBoundInfoState(nextBoundInfo);
    if (nextBoundInfo.consoleId !== undefined && nextBoundInfo.consoleId !== null) {
      updateConsoleBoundInfo(nextBoundInfo.consoleId, nextBoundInfo);
    }
  }, []);

  // 注册 consoleRef 到全局 map 中
  useEffect(() => {
    const consoleId = boundInfo.consoleId;
    registerConsoleEditor(consoleId, consoleRef);
    registerSearchResult(consoleId, searchResultRef);

    return () => {
      // 组件卸载时移除
      unregisterConsoleEditor(consoleId);
      unregisterSearchResult(consoleId);
    };
  }, [boundInfo.consoleId]);

  useEffect(() => {
    if (loadSQL) {
      loadSQL()
        .then((sql) => {
          if (sql) {
            consoleRef.current?.editorRef?.setValue(sql, 'cover');
          } else {
            console.warn('loadSQL returned empty value');
          }
        })
        .catch((err) => {
          console.error('Failed to load SQL:', err);
        });
    }
  }, [loadSQL]);

  return (
    <div className={classnames(styles.sqlExecute)}>
      <DraggableContainer layout="column" className={styles.boxRightCenter}>
        <div ref={draggableRef} className={styles.boxRightConsole}>
          <ConsoleEditor
            ref={consoleRef}
            source="workspace"
            defaultValue={initDDL}
            boundInfo={boundInfo}
            setBoundInfo={setBoundInfo}
            hasAi2Lang={true}
            isActive={activeConsoleId === boundInfo.consoleId}
            onExecuteSQL={(sql, selectedInfo) => {
              searchResultRef.current?.handleExecuteSQL(sql, {
                scriptStartLine: selectedInfo?.startLine || 1,
              });
            }}
          />
        </div>
        <div className={styles.boxRightResult}>
          <SearchResult
            isActive={activeConsoleId === boundInfo.consoleId}
            ref={searchResultRef}
            executeSqlParams={boundInfo}
            exportFileName={props.consoleTitle}
            initialResult={props.initialResult}
            onLocateStatement={(result) => {
              consoleRef.current?.editorRef?.locateStatement(
                result.statementStartLine || 1,
                result.statementEndLine || result.statementStartLine || 1,
                result.errorLine,
              );
            }}
          />
        </div>
      </DraggableContainer>
    </div>
  );
});

export default SQLExecute;

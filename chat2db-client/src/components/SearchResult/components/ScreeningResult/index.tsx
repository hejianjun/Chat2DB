import React, { memo, useEffect, useContext } from 'react';
import classnames from 'classnames';
import styles from './index.less';
import Iconfont from '@/components/Iconfont';
import SingleFileMonacoEditor from '@/components/SingleFileMonacoEditor';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import { IExecuteSqlParams } from '@/service/sql';
import { Context } from '@/components/SearchResult';
import { DatabaseTypeCode } from '@/constants';
import { buildMongodbFindQuery } from '@/utils/database';

interface IProps {
  className?: string;
  promptWord: any[];
  getTableData: (params?: Partial<IExecuteSqlParams>) => void;
  databaseType?: DatabaseTypeCode;
  tableName?: string;
}

const keywordHintList = [
  'AND',
  'OR',
  'NOT',
  'IS',
  'NULL',
  'IN',
  'IS NOT NULL',
  'IS NULL',
  'IS NOT',
  'NOT IN',
  'EXISTS',
  'BETWEEN',
  'LIKE',
  'ASC',
  'DESC',
];

const mongodbKeywordHintList = [
  '$and',
  '$or',
  '$not',
  '$nor',
  '$eq',
  '$ne',
  '$gt',
  '$gte',
  '$lt',
  '$lte',
  '$in',
  '$nin',
  '$exists',
  '$regex',
];

export default memo<IProps>((props) => {
  const { promptWord, getTableData, databaseType, tableName } = props;
  const { notChangedSql } = useContext(Context);
  const isMongodb = databaseType === DatabaseTypeCode.MONGODB;
  const [isActive, setIsActive] = React.useState(false);
  const keywordHintRef = React.useRef<any>(null);
  const fieldHintRef = React.useRef<any>(null);
  const whereSingleFileMonacoEditorRef = React.useRef<any>(null);
  const orderBySingleFileMonacoEditorRef = React.useRef<any>(null);

  useEffect(() => {
    keywordHintRef.current && keywordHintRef.current.dispose();
    fieldHintRef.current && fieldHintRef.current.dispose();
    if (isActive) {
      registerPromptWord();
    }
  }, [promptWord, isActive]);

  const registerPromptWord = () => {
    const buildRange = (model: monaco.editor.ITextModel, position: monaco.Position) => {
      const word = model.getWordUntilPosition(position);
      return new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
    };

    const buildSuggestions = (range: monaco.Range) =>
      promptWord.slice(isMongodb ? 0 : 1).map((item) => {
        return {
          insertText: item.name,
          kind: monaco.languages.CompletionItemKind.Field,
          label: item.name,
          range,
        };
      });

    const provideCompletionItems = (model: monaco.editor.ITextModel, position: monaco.Position) => {
      return {
        suggestions: buildSuggestions(buildRange(model, position)),
      };
    };

    fieldHintRef.current = monaco.languages.registerCompletionItemProvider('sql', {
      provideCompletionItems,
      triggerCharacters: [],
    });

    keywordHintRef.current = monaco.languages.registerCompletionItemProvider('sql', {
      provideCompletionItems: (model, position) => {
        const range = buildRange(model, position);
        return {
          suggestions: (isMongodb ? mongodbKeywordHintList : keywordHintList).map((item) => {
            return {
              insertText: item,
              kind: monaco.languages.CompletionItemKind.Keyword,
              label: item,
              range,
            };
          }),
        };
      },
      triggerCharacters: [],
    });
  };

  const search = () => {
    const filterValue = whereSingleFileMonacoEditorRef.current?.getAllContent().trim() || '';
    const sortValue = orderBySingleFileMonacoEditorRef.current?.getAllContent().trim() || '';
    let sql: string;
    if (isMongodb && tableName) {
      sql = buildMongodbFindQuery(tableName, filterValue, sortValue);
    } else {
      sql = filterValue ? notChangedSql + ' WHERE ' + filterValue : notChangedSql;
      sql = sortValue ? sql + ' ORDER BY ' + sortValue : sql;
    }
    getTableData({ sql });
  };

  const focusChange = (_isActive: boolean) => {
    setIsActive(_isActive);
  };

  return (
    <div className={styles.screeningResult}>
      <div className={styles.whereBox}>
        <div className={styles.titleBox}>
          <Iconfont box boxSize={20} classNameBox={styles.titleIcon} code="&#xe66a;" />
          <div
            className={classnames(styles.title, {
              [styles.activeTitle]: true,
            })}
          >
            {isMongodb ? 'FILTER' : 'WHERE'}
          </div>
        </div>
        <SingleFileMonacoEditor
          ref={whereSingleFileMonacoEditorRef}
          focusChange={focusChange}
          handelEnter={search}
          className={styles.monacoEditor}
        />
      </div>
      <div className={styles.orderByBox}>
        <div className={styles.titleBox}>
          <Iconfont box boxSize={20} classNameBox={styles.titleIcon} code="&#xe69a;" />
          <div
            className={classnames(styles.title, {
              [styles.activeTitle]: true,
            })}
          >
            {isMongodb ? 'SORT' : 'ORDER BY'}
          </div>
        </div>
        <SingleFileMonacoEditor
          ref={orderBySingleFileMonacoEditorRef}
          focusChange={focusChange}
          handelEnter={search}
          className={styles.monacoEditor}
        />
      </div>
    </div>
  );
});

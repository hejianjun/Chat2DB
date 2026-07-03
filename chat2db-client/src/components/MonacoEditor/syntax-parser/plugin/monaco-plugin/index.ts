/* eslint-disable no-restricted-globals */
/* eslint-disable no-case-declarations */
/* eslint-disable no-use-before-define */
/* eslint-disable no-plusplus */
/* eslint-disable no-param-reassign */
import * as _ from 'lodash';
import { IParseResult } from '../..';
import { DefaultOpts, IMonacoVersion, IParserType } from './default-opts';
import { createParserWorker } from './worker-factory';
import { ICompletionItem, ITableInfo, IJoinTableInfo, reader, ICursorInfo, mysqlParser } from '../sql-parser';

const supportedMonacoEditorVersion = ['0.13.2', '0.15.6'];

const getCurrentStatement = (value: string, cursorOffset: number) => {
  const statements = value.slice(0, cursorOffset).split(';');
  return statements[statements.length - 1] || '';
};

const isDdlStatement = (statement: string) => {
  return /^(CREATE|ALTER|DROP|TRUNCATE|RENAME)\b/i.test(statement.replace(/\s+/g, ' ').trimStart());
};

const ddlRootKeywords = ['CREATE', 'ALTER', 'DROP', 'TRUNCATE', 'RENAME'];

// Identify an unfinished root keyword such as CREAT or ALTE, but exclude the already completed keyword.
const getDdlRootPrefix = (statement: string) => {
  const inputValue = statement.trim().toUpperCase();
  if (inputValue.length < 2 || !/^[A-Z]+$/.test(inputValue)) {
    return null;
  }
  const hasMatchingKeyword = ddlRootKeywords.some(
    (keyword) => keyword !== inputValue && keyword.startsWith(inputValue),
  );
  return hasMatchingKeyword ? inputValue : null;
};

// The bundled parser only understands SELECT statements, so DDL documents must bypass its diagnostics.
const getDdlStatementType = (value: string) => {
  const sqlWithoutComments = value.replace(/((?:#|--)[\s\S]*?(?:\r?\n|$)|\/\*[\s\S]*?(?:\*\/|$))/g, '');
  const ddlStatement = sqlWithoutComments.split(';').find((statement) => isDdlStatement(statement));
  const normalizedDdlStatement = ddlStatement?.replace(/\s+/g, ' ').trimStart();
  return normalizedDdlStatement?.match(/^[A-Z]+/i)?.[0]?.toUpperCase() || null;
};

// Keep DDL navigation suggestions small and contextual instead of returning the entire dialect keyword list.
const createDdlKeywordItems = (keywords: string[]): ICompletionItem[] => {
  return keywords.map((keyword, index) => ({
    label: keyword,
    insertText: keyword,
    sortText: `A${index.toString().padStart(2, '0')}`,
    detail: '(DDL 关键字)',
  }));
};

// Column definitions should prioritize data types after the column name.
const getDataTypeCompletionItems = async (opts: Partial<DefaultOpts>) => {
  const completionItems = (await opts.onSuggestFunctionName?.('')) || [];
  return completionItems.filter((item) => item.detail === '(数据类型)');
};

const getDdlCompletionItems = async (
  statement: string,
  opts: Partial<DefaultOpts>,
): Promise<ICompletionItem[] | null> => {
  const normalizedStatement = statement.replace(/\s+/g, ' ').trimStart();
  const ddlRootPrefix = getDdlRootPrefix(normalizedStatement);
  if (ddlRootPrefix) {
    return createDdlKeywordItems(ddlRootKeywords.filter((keyword) => keyword.startsWith(ddlRootPrefix)));
  }

  if (!isDdlStatement(normalizedStatement)) {
    return null;
  }

  if (/^ALTER\s*$/i.test(normalizedStatement)) {
    return createDdlKeywordItems(['TABLE', 'DATABASE', 'SCHEMA', 'VIEW', 'INDEX']);
  }

  if (/^CREATE\s*$/i.test(normalizedStatement)) {
    return createDdlKeywordItems(['TABLE', 'VIEW', 'INDEX', 'UNIQUE INDEX', 'DATABASE', 'SCHEMA']);
  }

  if (/^DROP\s*$/i.test(normalizedStatement)) {
    return createDdlKeywordItems(['TABLE', 'VIEW', 'INDEX', 'DATABASE', 'SCHEMA']);
  }

  if (/^TRUNCATE\s*$/i.test(normalizedStatement)) {
    return createDdlKeywordItems(['TABLE']);
  }

  if (/^RENAME\s*$/i.test(normalizedStatement)) {
    return createDdlKeywordItems(['TABLE']);
  }

  const tableNameContext =
    /^(?:ALTER|DROP|TRUNCATE)\s+TABLE(?:\s+IF\s+EXISTS)?\s+(?:[`"[\]\w.]+)?$/i.test(normalizedStatement) ||
    /^CREATE\s+(?:UNIQUE\s+)?INDEX\s+[`"[\]\w.]+\s+ON\s+(?:[`"[\]\w.]+)?$/i.test(normalizedStatement) ||
    /^RENAME\s+TABLE\s+(?:[`"[\]\w.]+)?$/i.test(normalizedStatement);
  if (tableNameContext) {
    return opts.onSuggestTableNames?.() || [];
  }

  const alterTableMatch = normalizedStatement.match(/^ALTER\s+TABLE\s+([`"[\]\w.]+)\s+(.+)$/i);
  const alterTableActionContext = normalizedStatement.match(/^ALTER\s+TABLE\s+([`"[\]\w.]+)\s+$/i);
  if (alterTableActionContext) {
    return createDdlKeywordItems([
      'ADD COLUMN',
      'DROP COLUMN',
      'MODIFY COLUMN',
      'CHANGE COLUMN',
      'ALTER COLUMN',
      'ADD CONSTRAINT',
      'ADD INDEX',
      'DROP INDEX',
      'RENAME TO',
    ]);
  }

  if (alterTableMatch) {
    const action = alterTableMatch[2];
    const dataTypeContext =
      /^(?:(?:ADD|MODIFY)\s+(?:COLUMN\s+)?[`"[\]\w]+|CHANGE\s+(?:COLUMN\s+)?[`"[\]\w]+\s+[`"[\]\w]+|ALTER\s+(?:COLUMN\s+)?[`"[\]\w]+\s+TYPE)\s+$/i.test(
        action,
      );
    if (dataTypeContext) {
      return getDataTypeCompletionItems(opts);
    }

    const fieldContext =
      /^(?:DROP\s+(?:COLUMN\s+)?|ALTER\s+(?:COLUMN\s+)?|MODIFY\s+(?:COLUMN\s+)?|CHANGE\s+(?:COLUMN\s+)?)(?:[`"[\]\w]*)$/i.test(
        action,
      );

    if (fieldContext) {
      const tableInfo: ITableInfo = {
        tableName: { type: 'identifier', value: alterTableMatch[1] },
        namespace: { type: 'identifier', value: '' },
      };
      return opts.onSuggestTableFields?.(tableInfo, '', {} as any) || [];
    }
  }

  if (/^CREATE\s+TABLE\b[\s\S]*(?:\(|,)\s*[`"[\]\w]+\s+$/i.test(normalizedStatement)) {
    return getDataTypeCompletionItems(opts);
  }

  const inputValue = normalizedStatement.match(/([A-Z_][A-Z0-9_]*)$/i)?.[1] || '';
  return opts.onSuggestFunctionName?.(inputValue) || [];
};

export function monacoSqlAutocomplete(monaco: any, editor: any, opts?: Partial<DefaultOpts>) {
  opts = _.defaults(opts || {}, new DefaultOpts(monaco));

  if (supportedMonacoEditorVersion.indexOf(opts.monacoEditorVersion) === -1) {
    throw Error(
      `monaco-editor version ${
        opts.monacoEditorVersion
      } is not allowed, only support ${supportedMonacoEditorVersion.join(' ')}`,
    );
  }

  // Register completion provider
  // Get parser info and show error.
  let currentParserPromise: any = null;
  let editVersion = 0;
  let disposed = false;

  // Initialize parser promise with current editor content
  const initParserPromise = () => {
    const model = editor.getModel();
    if (model) {
      currentParserPromise = asyncParser(editor.getValue(), model.getOffsetAt(editor.getPosition()), opts.parserType);
    }
  };

  // Initialize on first load
  initParserPromise();
  const initialModel = editor.getModel();
  if (initialModel && getDdlStatementType(initialModel.getValue())) {
    monaco.editor.setModelMarkers(initialModel, opts.language, []);
  }

  const completionProvider = monaco.languages.registerCompletionItemProvider(opts.language, {
    triggerCharacters: ' $.:{}=abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
    provideCompletionItems: async (model: any, position: any) => {
      const currentEditVersion = editVersion;

      const ddlCompletionItems = await getDdlCompletionItems(
        getCurrentStatement(model.getValue(), model.getOffsetAt(position)),
        opts,
      );
      if (ddlCompletionItems) {
        return returnCompletionItemsByVersion(_.uniqBy(ddlCompletionItems, 'label'), opts.monacoEditorVersion);
      }

      // If currentParserPromise is null, initialize it
      if (!currentParserPromise) {
        initParserPromise();
      }

      const parseResult: IParseResult = await currentParserPromise;

      if (!parseResult) {
        return returnCompletionItemsByVersion([], opts.monacoEditorVersion);
      }

      if (currentEditVersion !== editVersion) {
        return returnCompletionItemsByVersion([], opts.monacoEditorVersion);
      }

      const cursorInfo = await reader.getCursorInfo(parseResult.ast, parseResult.cursorKeyPath);

      const parserSuggestion = opts.pipeKeywords(parseResult.nextMatchings);

      // 当 cursorInfo 为 null 但解析失败时，尝试从 SELECT 语句获取字段
      if (!cursorInfo && parseResult.ast && parseResult.error) {
        const fallbackFields = await reader.getFieldsFromStatement(
          parseResult.ast,
          [], // 空的 cursorKeyPath
          opts.onSuggestTableFields,
        );

        if (fallbackFields && fallbackFields.length > 0) {
          const uniqueFallbackFields = _.uniqBy(fallbackFields, 'label');
          const functionNames = await opts.onSuggestFunctionName('');
          const result = uniqueFallbackFields.concat(functionNames).concat(parserSuggestion);
          return returnCompletionItemsByVersion(result, opts.monacoEditorVersion);
        }

        return returnCompletionItemsByVersion(parserSuggestion, opts.monacoEditorVersion);
      }

      if (!cursorInfo) {
        return returnCompletionItemsByVersion(parserSuggestion, opts.monacoEditorVersion);
      }

      switch (cursorInfo.type) {
        case 'tableField':
          const cursorRootStatementFields = await reader.getFieldsFromStatement(
            parseResult.ast,
            parseResult.cursorKeyPath,
            opts.onSuggestTableFields,
          );

          // 去重字段（避免重复）
          const uniqueFields = _.uniqBy(cursorRootStatementFields, 'label');

          // group.fieldName
          const groups = _.groupBy(
            uniqueFields.filter((cursorRootStatementField) => {
              return cursorRootStatementField.groupPickerName !== null;
            }),
            'groupPickerName',
          );

          const functionNames = await opts.onSuggestFunctionName(cursorInfo.token.value);

          const result = uniqueFields
            .concat(functionNames) // 函数名
            .concat(parserSuggestion) // SQL 关键字
            .concat(
              groups
                ? Object.keys(groups).map((groupName) => {
                    return opts.onSuggestFieldGroup(groupName);
                  })
                : [],
            );

          return returnCompletionItemsByVersion(result, opts.monacoEditorVersion);

        case 'tableFieldAfterGroup':
          // 字段 . 后面的部分
          const cursorRootStatementFieldsAfter = await reader.getFieldsFromStatement(
            parseResult.ast,
            parseResult.cursorKeyPath as any,
            opts.onSuggestTableFields,
          );

          // 去重并过滤
          const uniqueFieldsAfter = _.uniqBy(cursorRootStatementFieldsAfter, 'label');
          const filteredFields = uniqueFieldsAfter
            .filter((cursorRootStatementField: any) => {
              return (
                cursorRootStatementField.groupPickerName ===
                (cursorInfo as unknown as ICursorInfo<{ groupName: string }>).groupName
              );
            })
            .filter((field: any) => {
              // 过滤掉无效的补全项
              return (
                field && field.label && field.label.trim() !== '' && field.insertText && field.insertText.trim() !== ''
              );
            });

          // 字段排在最前面，关键字排在后面
          const sortedFields = [
            ...filteredFields, // 字段（sortText: B*）
            ...parserSuggestion.filter((item) => item.insertText && item.insertText.trim() !== ''), // SQL 关键字（sortText: W*）
          ];

          return returnCompletionItemsByVersion(sortedFields, opts.monacoEditorVersion);

        case 'joinTable':
          const joinTableNames = await opts.onSuggestJoinTables(
            cursorInfo as unknown as ICursorInfo<{ joinTableInfo: IJoinTableInfo }>,
          );

          return returnCompletionItemsByVersion(joinTableNames.concat(parserSuggestion), opts.monacoEditorVersion);

        case 'tableName':
          const tableNames = await opts.onSuggestTableNames(
            cursorInfo as unknown as ICursorInfo<{ tableInfo: ITableInfo }>,
          );

          return returnCompletionItemsByVersion(tableNames.concat(parserSuggestion), opts.monacoEditorVersion);
        case 'functionName':
          return opts.onSuggestFunctionName(cursorInfo.token.value);
        default:
          return returnCompletionItemsByVersion(parserSuggestion, opts.monacoEditorVersion);
      }
    },
  });

  // Listen for content changes and update parser promise
  const contentChangeDisposable = editor.onDidChangeModelContent((event: any) => {
    editVersion++;
    const currentEditVersion = editVersion;

    currentParserPromise = new Promise((resolve) => {
      setTimeout(() => {
        if (disposed) {
          resolve(null);
          return;
        }

        const model = editor.getModel();
        const cursorOffset = model.getOffsetAt(editor.getPosition());
        const currentStatement = getCurrentStatement(model.getValue(), cursorOffset);

        const ddlStatementType = getDdlStatementType(model.getValue());
        const ddlRootPrefix = getDdlRootPrefix(currentStatement);
        const completedDdlRoot = ddlRootKeywords.includes(currentStatement.trim().toUpperCase());
        if (ddlStatementType || ddlRootPrefix) {
          if (process.env.NODE_ENV === 'development') {
            console.debug(
              `[SQL 自动补全][DDL] ${ddlStatementType || ddlRootPrefix} 跳过 SELECT 语法诊断并清除错误标记`,
            );
          }
          monaco.editor.setModelMarkers(model, opts.language, []);
          // Refresh suggestions when a root keyword changes context or when whitespace starts the next DDL clause.
          if (ddlRootPrefix || completedDdlRoot || event.changes?.some((change: any) => /\s$/.test(change.text))) {
            editor.trigger('ddl-autocomplete', 'editor.action.triggerSuggest', {});
          }
          resolve(null);
          return;
        }

        asyncParser(editor.getValue(), cursorOffset, opts.parserType).then((parseResult) => {
          resolve(parseResult);

          if (disposed || currentEditVersion !== editVersion) {
            return;
          }

          opts.onParse(parseResult);

          if (parseResult.error) {
            // Check if input only contains comments and whitespace
            // Handle both \n and \r\n line endings, and use [\s\S] to match all characters including \r
            const textWithoutComments = editor
              .getValue()
              .replace(/((?:#|--)[\s\S]*?(?:\r?\n|$)|\/\*[\s\S]*?(?:\*\/|$))/g, '')
              .trim();

            // Only show error if there's actual SQL content (not just comments)
            if (textWithoutComments.length > 0) {
              const newReason =
                parseResult.error.reason === 'incomplete'
                  ? `Incomplete, expect next input: \n${parseResult.error.suggestions
                      .map((each: any) => {
                        return each.value;
                      })
                      .join('\n')}`
                  : `Wrong input, expect: \n${parseResult.error.suggestions
                      .map((each: any) => {
                        return each.value;
                      })
                      .join('\n')}`;

              const errorPosition = parseResult.error.token
                ? {
                    startLineNumber: model.getPositionAt(parseResult.error.token.position[0]).lineNumber,
                    startColumn: model.getPositionAt(parseResult.error.token.position[0]).column,
                    endLineNumber: model.getPositionAt(parseResult.error.token.position[1]).lineNumber,
                    endColumn: model.getPositionAt(parseResult.error.token.position[1]).column + 1,
                  }
                : {
                    startLineNumber: 0,
                    startColumn: 0,
                    endLineNumber: 0,
                    endColumn: 0,
                  };

              model.getPositionAt(parseResult.error.token);

              monaco.editor.setModelMarkers(model, opts.language, [
                {
                  ...errorPosition,
                  message: newReason,
                  severity: getSeverityByVersion(monaco, opts.monacoEditorVersion),
                },
              ]);
            } else {
              // Clear error markers for comment-only input
              monaco.editor.setModelMarkers(editor.getModel(), opts.language, []);
            }
          } else {
            monaco.editor.setModelMarkers(editor.getModel(), opts.language, []);
          }
        });
      });
    });
  });

  // Register hover provider and store it for disposal
  const hoverProviderDisposable = monaco.languages.registerHoverProvider(opts.language, {
    provideHover: async (model: any, position: any) => {
      if (isDdlStatement(getCurrentStatement(model.getValue(), model.getOffsetAt(position)))) {
        return null;
      }

      const parseResult: IParseResult = await asyncParser(
        editor.getValue(),
        model.getOffsetAt(position),
        opts.parserType,
      );

      const cursorInfo = await reader.getCursorInfo(parseResult.ast, parseResult.cursorKeyPath);

      if (!cursorInfo) {
        return null as any;
      }

      let contents: any = [];

      switch (cursorInfo.type) {
        case 'tableField':
          const extra = await reader.findFieldExtraInfo(
            parseResult.ast,
            cursorInfo,
            opts.onSuggestTableFields,
            parseResult.cursorKeyPath,
          );
          contents = await opts.onHoverTableField(cursorInfo.token.value, extra);
          break;
        case 'tableFieldAfterGroup':
          const extraAfter = await reader.findFieldExtraInfo(
            parseResult.ast,
            cursorInfo,
            opts.onSuggestTableFields,
            parseResult.cursorKeyPath,
          );
          contents = await opts.onHoverTableField(cursorInfo.token.value, extraAfter);
          break;
        case 'tableName':
          contents = await opts.onHoverTableName(cursorInfo as ICursorInfo);
          break;
        case 'functionName':
          contents = await opts.onHoverFunctionName(cursorInfo.token.value);
          break;
        default:
      }

      return {
        range: monaco.Range.fromPositions(
          model.getPositionAt(cursorInfo.token.position[0]),
          model.getPositionAt(cursorInfo.token.position[1] + 1),
        ),
        contents,
      };
    },
  });

  // Return disposable object
  return {
    dispose: () => {
      disposed = true;
      completionProvider.dispose();
      contentChangeDisposable.dispose();
      hoverProviderDisposable.dispose();
    },
  };
}

// 实例化一个 worker
// 注意：开发环境下使用同步解析避免 HMR 问题
// let worker: Worker | null = null;
// try {
//   worker = createParserWorker();
// } catch (error) {
//   console.warn('Failed to create worker, will use fallback:', error);
// }
const worker: Worker | null = null; // 暂时禁用 worker

const parserIndex = 0;

// 防抖：记录上一次补全时间，避免频繁触发
const lastCompletionTime = 0;
const COMPLETION_DEBOUNCE = 200; // 200ms 防抖

const asyncParser = async (text: string, index: number, parserType: IParserType): Promise<IParseResult> => {
  // 开发环境直接使用同步解析
  try {
    const result = mysqlParser(text, index);
    return Promise.resolve(result);
  } catch (error) {
    console.error('[Parser] 解析错误:', error);
    return Promise.reject(error);
  }
};

// const asyncParser = async (
//   text: string,
//   index: number,
//   parserType: IParserType,
// ): Promise<IParseResult> => {
//   // 如果 worker 可用，使用异步解析
//   if (worker) {
//     parserIndex++;
//     const currentParserIndex = parserIndex;
//
//     return new Promise((resolve, reject) => {
//       worker!.postMessage({ text, index, parserType });
//
//       worker!.onmessage = (event) => {
//         if (currentParserIndex === parserIndex) {
//           resolve(event.data);
//         } else {
//           reject();
//         }
//       };
//     });
//   } else {
//     // 回退方案：同步解析
//     try {
//       const result = mysqlParser(text, index);
//       return Promise.resolve(result);
//     } catch (error) {
//       console.error('Parser error:', error);
//       return Promise.reject(error);
//     }
//   }
// };

function returnCompletionItemsByVersion(value: ICompletionItem[], monacoVersion: IMonacoVersion) {
  switch (monacoVersion) {
    case '0.13.2':
      return value;
    case '0.15.6':
      return {
        suggestions: value,
      };
    default:
      throw Error('Not supported version');
  }
}

function getSeverityByVersion(monaco: any, monacoVersion: IMonacoVersion) {
  switch (monacoVersion) {
    case '0.13.2':
      return monaco.Severity.Error;
    case '0.15.6':
      return monaco.MarkerSeverity.Error;
    default:
      throw Error('Not supported version');
  }
}

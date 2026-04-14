/* eslint-disable no-restricted-globals */
/* eslint-disable no-case-declarations */
/* eslint-disable no-use-before-define */
/* eslint-disable no-plusplus */
/* eslint-disable no-param-reassign */
import * as _ from 'lodash';
import { IParseResult } from '../..';
import { DefaultOpts, IMonacoVersion, IParserType } from './default-opts';
import { createParserWorker } from './worker-factory';
import { mysqlParser } from '../sql-parser';
import {
  ICompletionItem,
  ITableInfo,
  reader,
  ICursorInfo,
} from '../sql-parser';

const supportedMonacoEditorVersion = ['0.13.2', '0.15.6'];

export function monacoSqlAutocomplete(
  monaco: any,
  editor: any,
  opts?: Partial<DefaultOpts>,
) {
  opts = _.defaults(opts || {}, new DefaultOpts(monaco));

  if (supportedMonacoEditorVersion.indexOf(opts.monacoEditorVersion) === -1) {
    throw Error(
      `monaco-editor version ${
        opts.monacoEditorVersion
      } is not allowed, only support ${supportedMonacoEditorVersion.join(' ')}`,
    );
  }

  // Get parser info and show error.
  let currentParserPromise: any = null;
  let editVersion = 0;

  editor.onDidChangeModelContent((event: any) => {
    editVersion++;
    const currentEditVersion = editVersion;

    currentParserPromise = new Promise((resolve) => {
      setTimeout(() => {
        const model = editor.getModel();

        asyncParser(
          editor.getValue(),
          model.getOffsetAt(editor.getPosition()),
          opts.parserType,
        ).then((parseResult) => {
          resolve(parseResult);

          if (currentEditVersion !== editVersion) {
            return;
          }

          opts.onParse(parseResult);

          if (parseResult.error) {
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
                  startLineNumber: model.getPositionAt(
                    parseResult.error.token.position[0],
                  ).lineNumber,
                  startColumn: model.getPositionAt(
                    parseResult.error.token.position[0],
                  ).column,
                  endLineNumber: model.getPositionAt(
                    parseResult.error.token.position[1],
                  ).lineNumber,
                  endColumn:
                    model.getPositionAt(parseResult.error.token.position[1])
                      .column + 1,
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
                severity: getSeverityByVersion(
                  monaco,
                  opts.monacoEditorVersion,
                ),
              },
            ]);
          } else {
            monaco.editor.setModelMarkers(editor.getModel(), opts.language, []);
          }
        });
      });
    });
  });

  monaco.languages.registerCompletionItemProvider(opts.language, {
    triggerCharacters:
      ' $.:{}=abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'.split(''),
    provideCompletionItems: async () => {
      console.log('[SQL 补全] === 开始补全流程 ===');
      const currentEditVersion = editVersion;
      const parseResult: IParseResult = await currentParserPromise;

      if (currentEditVersion !== editVersion) {
        console.log('[SQL 补全] 编辑已更新，取消当前补全');
        return returnCompletionItemsByVersion([], opts.monacoEditorVersion);
      }

      console.log('[SQL 补全] 解析结果:', {
        success: parseResult.success,
        hasError: !!parseResult.error,
        cursorKeyPath: parseResult.cursorKeyPath,
        nextMatchingsCount: parseResult.nextMatchings?.length || 0,
      });

      const cursorInfo = await reader.getCursorInfo(
        parseResult.ast,
        parseResult.cursorKeyPath,
      );

      console.log('[SQL 补全] 光标信息:', cursorInfo);

      const parserSuggestion = opts.pipeKeywords(parseResult.nextMatchings);

      console.log('[SQL 补全] 关键字补全数量:', parserSuggestion.length);

      if (!cursorInfo) {
        console.log('[SQL 补全] 无光标信息，返回关键字补全');
        return returnCompletionItemsByVersion(
          parserSuggestion,
          opts.monacoEditorVersion,
        );
      }

      console.log('[SQL 补全] 光标类型:', cursorInfo.type);

      switch (cursorInfo.type) {
        case 'tableField':
          console.log('[SQL 补全] 表字段补全模式');
          const cursorRootStatementFields = await reader.getFieldsFromStatement(
            parseResult.ast,
            parseResult.cursorKeyPath,
            opts.onSuggestTableFields,
          );

          console.log('[SQL 补全] 获取到字段数量:', cursorRootStatementFields.length);

          // 去重字段（避免重复）
          const uniqueFields = _.uniqBy(cursorRootStatementFields, 'label');
          console.log('[SQL 补全] 去重后字段数量:', uniqueFields.length);

          // group.fieldName
          const groups = _.groupBy(
            uniqueFields.filter((cursorRootStatementField) => {
              return cursorRootStatementField.groupPickerName !== null;
            }),
            'groupPickerName',
          );

          console.log('[SQL 补全] 分组信息:', Object.keys(groups));

          const functionNames = await opts.onSuggestFunctionName(
            cursorInfo.token.value,
          );

          const result = uniqueFields
            .concat(functionNames)  // 函数名
            .concat(parserSuggestion)  // SQL 关键字
            .concat(
              groups
                ? Object.keys(groups).map((groupName) => {
                    console.log('[SQL 补全] 添加分组:', groupName);
                    return opts.onSuggestFieldGroup(groupName);
                  })
                : [],
            );

          console.log('[SQL 补全] 最终补全项总数:', result.length);
          return returnCompletionItemsByVersion(
            result,
            opts.monacoEditorVersion,
          );
          
        case 'tableFieldAfterGroup':
          // 字段 . 后面的部分
          console.log('[SQL 补全] 表名限定字段模式，分组:', (cursorInfo as ICursorInfo<{ groupName: string }>).groupName);
          const cursorRootStatementFieldsAfter =
            await reader.getFieldsFromStatement(
              parseResult.ast,
              parseResult.cursorKeyPath as any,
              opts.onSuggestTableFields,
            );

          console.log('[SQL 补全] 过滤前字段数量:', cursorRootStatementFieldsAfter.length);

          // 去重并过滤
          const uniqueFieldsAfter = _.uniqBy(cursorRootStatementFieldsAfter, 'label');
          const filteredFields = uniqueFieldsAfter
            .filter((cursorRootStatementField: any) => {
              return (
                cursorRootStatementField.groupPickerName ===
                (cursorInfo as ICursorInfo<{ groupName: string }>).groupName
              );
            })
            .filter((field: any) => {
              // 过滤掉无效的补全项
              return field && field.label && field.label.trim() !== '' && field.insertText && field.insertText.trim() !== '';
            });

          console.log('[SQL 补全] 过滤后字段数量:', filteredFields.length);

          // 字段排在最前面，关键字排在后面
          const sortedFields = [
            ...filteredFields,  // 字段（sortText: B*）
            ...parserSuggestion.filter(item => item.insertText && item.insertText.trim() !== ''),  // SQL 关键字（sortText: W*）
          ];

          console.log('[SQL 补全] 最终补全项总数:', sortedFields.length);
          return returnCompletionItemsByVersion(
            sortedFields,
            opts.monacoEditorVersion,
          );
        case 'tableName':
          console.log('[SQL 补全] 表名补全模式');
          const tableNames = await opts.onSuggestTableNames(
            cursorInfo as ICursorInfo<ITableInfo>,
          );

          console.log('[SQL 补全] 表名数量:', tableNames.length);
          return returnCompletionItemsByVersion(
            tableNames.concat(parserSuggestion),
            opts.monacoEditorVersion,
          );
        case 'functionName':
          console.log('[SQL 补全] 函数名补全模式');
          return opts.onSuggestFunctionName(cursorInfo.token.value);
        default:
          console.log('[SQL 补全] 默认模式，返回关键字补全');
          return returnCompletionItemsByVersion(
            parserSuggestion,
            opts.monacoEditorVersion,
          );
      }
    },
  });

  monaco.languages.registerHoverProvider(opts.language, {
    provideHover: async (model: any, position: any) => {
      const parseResult: IParseResult = await asyncParser(
        editor.getValue(),
        model.getOffsetAt(position),
        opts.parserType,
      );

      const cursorInfo = await reader.getCursorInfo(
        parseResult.ast,
        parseResult.cursorKeyPath,
      );

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
          contents = await opts.onHoverTableField(
            cursorInfo.token.value,
            extra,
          );
          break;
        case 'tableFieldAfterGroup':
          const extraAfter = await reader.findFieldExtraInfo(
            parseResult.ast,
            cursorInfo,
            opts.onSuggestTableFields,
            parseResult.cursorKeyPath,
          );
          contents = await opts.onHoverTableField(
            cursorInfo.token.value,
            extraAfter,
          );
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

let parserIndex = 0;

const asyncParser = async (
  text: string,
  index: number,
  parserType: IParserType,
): Promise<IParseResult> => {
  // 开发环境直接使用同步解析
  try {
    console.log('[Parser] 使用同步解析');
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

function returnCompletionItemsByVersion(
  value: ICompletionItem[],
  monacoVersion: IMonacoVersion,
) {
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

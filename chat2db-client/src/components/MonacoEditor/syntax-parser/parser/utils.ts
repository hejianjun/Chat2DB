import { IAst } from './define';

export const compareIgnoreLowerCaseWhenString = (source: any, target: any) => {
  if (typeof source === 'string' && typeof target === 'string') {
    return source.toLowerCase() === target.toLowerCase();
  }
  return source === target;
};

export const binaryRecursionToArray = (ast: IAst[]) => {
  if (ast[1]) {
    return [ast[0]].concat(ast[1][1]);
  }

  return [ast[0]];
};

export function tailCallOptimize<T>(f: T): T {
  let value: any;
  let active = false;
  const accumulated: any[] = [];
  return function accumulator(this: any) {
    // eslint-disable-next-line prefer-rest-params
    accumulated.push(arguments);
    if (!active) {
      active = true;
      while (accumulated.length) {
        // eslint-disable-next-line babel/no-invalid-this
        value = (f as any).apply(this, accumulated.shift());
      }
      active = false;
      return value;
    }
  } as any;
}

function findNearestTokenBeforeCursor(obj: any, cursorIndex: number, path?: string): { path: string; token: any; distance: number } | null {
  // eslint-disable-next-line no-param-reassign
  path = path || '';
  let nearest: { path: string; token: any; distance: number } | null = null;
  
  // eslint-disable-next-line guard-for-in
  for (const key in obj) {
    if (obj[key] && obj[key].token === true && obj[key].position) {
      const tokenEnd = obj[key].position[1] + 1;
      if (tokenEnd <= cursorIndex) {
        const distance = cursorIndex - tokenEnd;
        if (!nearest || distance < nearest.distance) {
          nearest = {
            path: path === '' ? key : `${path}.${key}`,
            token: obj[key],
            distance,
          };
        }
      }
    }
    if (typeof obj[key] === 'object' && obj[key] !== null) {
      const childNearest = findNearestTokenBeforeCursor(obj[key], cursorIndex, path === '' ? key : `${path}.${key}`);
      if (childNearest && (!nearest || childNearest.distance < nearest.distance)) {
        nearest = childNearest;
      }
    }
  }
  return nearest;
}

export function getPathByCursorIndexFromAst(obj: any, cursorIndex: number, path?: string) {
  // eslint-disable-next-line no-param-reassign
  path = path || '';
  let fullpath = '';
  
  // 首先尝试找到光标所在的 token
  // eslint-disable-next-line guard-for-in
  for (const key in obj) {
    if (
      obj[key] &&
      obj[key].token === true &&
      obj[key].position[0] <= cursorIndex &&
      obj[key].position[1] + 1 >= cursorIndex
    ) {
      if (path === '') {
        return key;
      }
      return `${path}.${key}`;
    }
    if (typeof obj[key] === 'object' && obj[key] !== null) {
      fullpath = getPathByCursorIndexFromAst(obj[key], cursorIndex, path === '' ? key : `${path}.${key}`) || fullpath;
    }
  }
  
  // 如果找不到光标所在的 token，尝试找最近的 token
  if (!fullpath) {
    const nearest = findNearestTokenBeforeCursor(obj, cursorIndex, path);
    if (nearest && nearest.distance <= 5) {
      // 如果光标距离最近的 token 结束位置不超过 5 个字符，返回该路径
      // 这可以帮助处理光标在空格后面的情况
      return nearest.path;
    }
  }
  
  return fullpath;
}

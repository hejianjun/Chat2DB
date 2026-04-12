import { RefObject } from 'react';
import { IConsoleRef } from '@/components/ConsoleEditor';

// 存储所有 consoleId 对应的 editor ref
const consoleEditorMap = new Map<number, RefObject<IConsoleRef>>();

export const registerConsoleEditor = (consoleId: number, ref: RefObject<IConsoleRef>) => {
  consoleEditorMap.set(consoleId, ref);
};

export const unregisterConsoleEditor = (consoleId: number) => {
  consoleEditorMap.delete(consoleId);
};

export const getConsoleEditor = (consoleId: number) => {
  return consoleEditorMap.get(consoleId);
};

import { UseBoundStoreWithEqualityFn, createWithEqualityFn } from 'zustand/traditional';
import { devtools, persist } from 'zustand/middleware';
import { shallow } from 'zustand/shallow';
import { StoreApi } from 'zustand';

import { DateTimeFormat, IAiConfig, IDateTimeDisplayConfig, SYSTEM_TIME_ZONE } from '@/typings/setting';
import { AIType, IRemainingUse } from '@/typings/ai';
import configService from '@/service/config';
import aiService from '@/service/ai';

export interface ISettingState {
  aiConfig: IAiConfig;
  dateTimeDisplay: IDateTimeDisplayConfig;
  remainingUse?: IRemainingUse;
  hasWhite: boolean;
  holdingService: boolean;
}

export const DEFAULT_DATE_TIME_DISPLAY: IDateTimeDisplayConfig = {
  format: DateTimeFormat.DATE_TIME,
  timeZone: SYSTEM_TIME_ZONE,
};

const initSetting: ISettingState = {
  remainingUse: undefined,
  aiConfig: {
    aiSqlSource: AIType.OPENAI,
    apiKey: '',
  },
  dateTimeDisplay: DEFAULT_DATE_TIME_DISPLAY,
  hasWhite: false,
  holdingService: false,
};

export const useSettingStore: UseBoundStoreWithEqualityFn<StoreApi<ISettingState>> = createWithEqualityFn(
  devtools(
    persist(
      () => initSetting,
      {
        name: 'global-setting',
        getStorage: () => localStorage,
        // 工作区的状态只保存 layout布局信息
        partialize: (state: ISettingState) => ({
          holdingService: state.holdingService,
          dateTimeDisplay: state.dateTimeDisplay,
        }),
      },
    ),
  ),
  shallow,
);

export const setAiConfig = (aiConfig: IAiConfig) => {
  useSettingStore.setState({ aiConfig });
};

export const setRemainUse = (remainingUse?: IRemainingUse) => {
  useSettingStore.setState({ remainingUse });
};

export const setAiWithWhite = (hasWhite: boolean) => {
  useSettingStore.setState({ hasWhite });
};

export const updateAiWithWhite = (apiKey: string) => {
  configService.getAiWhiteAccess({ apiKey: apiKey ?? '' }).then((res) => {
    setAiWithWhite(res);
  });
};

export const fetchRemainingUse = (apiKey: string) => {
  const currentState = useSettingStore.getState();
  if (!apiKey || currentState.aiConfig.aiSqlSource !== AIType.OPENAI) {
    setRemainUse(undefined);
    return;
  }
  aiService.getRemainingUse().then((res) => {
    setRemainUse(res);
  });
};

export const setHoldingService = (holdingService: boolean) => {
  useSettingStore.setState({ holdingService });
};

export const setDateTimeDisplay = (dateTimeDisplay: IDateTimeDisplayConfig) => {
  useSettingStore.setState({ dateTimeDisplay });
};

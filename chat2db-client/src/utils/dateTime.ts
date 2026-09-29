import {
  DateTimeFormat,
  IDateTimeDisplayConfig,
  SYSTEM_TIME_ZONE,
} from '@/typings/setting';

const ISO_INSTANT_PATTERN =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/i;
const LOCAL_DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?)?$/;
const LOCAL_TIME_PATTERN = /^(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/;
const FALLBACK_TIME_ZONES = [
  'Asia/Shanghai',
  'Asia/Tokyo',
  'Asia/Singapore',
  'Europe/London',
  'Europe/Paris',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Australia/Sydney',
];

interface IDateTimeParts {
  year?: string;
  month?: string;
  day?: string;
  hour?: string;
  minute?: string;
  second?: string;
  millisecond?: string;
}

export function getSystemTimeZone(): string {
  return new Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function isValidTimeZone(timeZone: string): boolean {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone }).format();
    return true;
  } catch {
    return false;
  }
}

export function resolveTimeZone(timeZone: string): string {
  if (timeZone === SYSTEM_TIME_ZONE) {
    return getSystemTimeZone();
  }
  return isValidTimeZone(timeZone) ? timeZone : getSystemTimeZone();
}

export function getSupportedTimeZones(): string[] {
  const intlWithSupportedValues = Intl as typeof Intl & {
    supportedValuesOf?: (key: 'timeZone') => string[];
  };
  const timeZones = intlWithSupportedValues.supportedValuesOf?.('timeZone') || FALLBACK_TIME_ZONES;
  return Array.from(new Set(timeZones.filter(isValidTimeZone))).sort();
}

function normalizeMilliseconds(value?: string): string {
  return (value || '').padEnd(3, '0').slice(0, 3);
}

function formatParts(parts: IDateTimeParts, format: DateTimeFormat): string {
  const dateSeparator = format === DateTimeFormat.DATE_TIME_SLASH ? '/' : '-';
  const date =
    parts.year && parts.month && parts.day
      ? `${parts.year}${dateSeparator}${parts.month}${dateSeparator}${parts.day}`
      : '';
  const time =
    parts.hour && parts.minute
      ? `${parts.hour}:${parts.minute}:${parts.second || '00'}`
      : '';
  const millisecond =
    format === DateTimeFormat.DATE_TIME_MILLIS && time
      ? `.${normalizeMilliseconds(parts.millisecond)}`
      : '';

  if (date && time) {
    return `${date} ${time}${millisecond}`;
  }
  return date || `${time}${millisecond}`;
}

function dateParts(date: Date, timeZone: string): IDateTimeParts {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  });
  const parts = formatter.formatToParts(date);
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value;
  return {
    year: value('year'),
    month: value('month'),
    day: value('day'),
    hour: value('hour'),
    minute: value('minute'),
    second: value('second'),
    millisecond: value('fractionalSecond'),
  };
}

function timeZoneOffset(date: Date, timeZone: string): string {
  if (timeZone === 'UTC') {
    return 'Z';
  }
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    timeZoneName: 'longOffset',
  });
  const offsetName = formatter
    .formatToParts(date)
    .find((part) => part.type === 'timeZoneName')
    ?.value.replace('GMT', '');
  if (!offsetName) {
    return 'Z';
  }
  const match = offsetName.match(/^([+-])(\d{1,2})(?::?(\d{2}))?$/);
  if (!match) {
    return 'Z';
  }
  return `${match[1]}${match[2].padStart(2, '0')}:${match[3] || '00'}`;
}

function formatInstant(value: string, config: IDateTimeDisplayConfig): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const timeZone = resolveTimeZone(config.timeZone);
  const parts = dateParts(date, timeZone);
  if (config.format === DateTimeFormat.ISO_8601) {
    const dateTime =
      `${parts.year}-${parts.month}-${parts.day}T` +
      `${parts.hour}:${parts.minute}:${parts.second}.${parts.millisecond}`;
    return `${dateTime}${timeZoneOffset(date, timeZone)}`;
  }
  return formatParts(parts, config.format);
}

function formatLocalValue(value: string, config: IDateTimeDisplayConfig): string {
  const match = value.match(LOCAL_DATE_TIME_PATTERN) || value.match(LOCAL_TIME_PATTERN);
  if (!match) {
    return value;
  }
  const isDateTime = match.length === 8;
  const parts: IDateTimeParts = isDateTime
    ? {
        year: match[1],
        month: match[2],
        day: match[3],
        hour: match[4],
        minute: match[5],
        second: match[6],
        millisecond: match[7],
      }
    : {
        hour: match[1],
        minute: match[2],
        second: match[3],
        millisecond: match[4],
      };
  if (config.format === DateTimeFormat.ISO_8601) {
    const date = parts.year ? `${parts.year}-${parts.month}-${parts.day}` : '';
    const time = parts.hour ? `${parts.hour}:${parts.minute}:${parts.second || '00'}` : '';
    const millisecond = parts.millisecond ? `.${normalizeMilliseconds(parts.millisecond)}` : '';
    return date && time ? `${date}T${time}${millisecond}` : date || `${time}${millisecond}`;
  }
  return formatParts(parts, config.format);
}

export function formatDateTimeValue(value: string, config: IDateTimeDisplayConfig): string {
  if (!value) {
    return value;
  }
  if (ISO_INSTANT_PATTERN.test(value)) {
    return formatInstant(value, config);
  }
  return formatLocalValue(value, config);
}

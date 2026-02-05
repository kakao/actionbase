export type CommandCategory = 'DDL' | 'DML' | 'GET' | 'SCAN' | 'COUNT' | 'UTIL' | 'ETC';

const COMMAND_CATEGORIES: Record<string, CommandCategory> = {
  create: 'DDL',
  load: 'DDL',
  mutate: 'DML',
  get: 'GET',
  scan: 'SCAN',
  count: 'COUNT',
  use: 'UTIL',
  show: 'UTIL',
  desc: 'UTIL',
  context: 'UTIL',
  debug: 'UTIL',
  guide: 'UTIL',
  help: 'UTIL',
  exit: 'UTIL',
};

export const getCommandCategory = (command: string): CommandCategory => {
  const trimmed = command.trim();
  const firstWord = trimmed.split(/\s+/)[0].toLowerCase();
  return COMMAND_CATEGORIES[firstWord] || 'UTIL';
};

const PROXIED_API_PATTERNS: Record<CommandCategory, string | null> = {
  DDL: '/graph/v2/service/{db}/label/{table}',
  DML: '/graph/v3/databases/{db}/tables/{table}/edges',
  GET: '/graph/v3/databases/{db}/tables/{table}/edges/get',
  SCAN: '/graph/v3/databases/{db}/tables/{table}/edges/scan/{index}',
  COUNT: '/graph/v3/databases/{db}/tables/{table}/edges/count',
  UTIL: null,
  ETC: null,
};

export const getProxiedApiPattern = (command: string): string | null => {
  const category = getCommandCategory(command);
  return PROXIED_API_PATTERNS[category];
};

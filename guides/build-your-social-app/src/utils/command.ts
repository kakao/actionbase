export type CommandCategory = 'DDL' | 'DML' | 'QUERY' | 'UTIL';

const COMMAND_CATEGORIES: Record<string, CommandCategory> = {
  create: 'DDL',
  load: 'DDL',
  mutate: 'DML',
  get: 'QUERY',
  scan: 'QUERY',
  count: 'QUERY',
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

import {apiFetch} from './client';
import {DATABASE, DIRECTION, TABLE} from "../constants";

const DEFAULT_LIMIT = 25;
const INDEX = {
  CREATED_AT_DESC: 'created_at_desc',
} as const;

export function getDatabase(
  name: string,
  enableLogging: boolean = true
) {
  return apiFetch<DatabaseEntity | undefined>(
    `/graph/v2/service/${name}`,
    {
      headers: {
        'Content-Type': 'application/json'
      }
    },
    enableLogging
  );
}

export function getTable(
  database: string,
  name: string,
  enableLogging: boolean = true
) {
  return apiFetch<TableEntity | undefined>(
    `/graph/v2/service/${database}/label/${name}`,
    {
      headers: {
        'Content-Type': 'application/json'
      }
    },
    enableLogging
  );
}

export function get(
  database: string,
  table: string,
  source: any,
  target: any,
  enableLogging: boolean = true
) {
  return apiFetch<DataPayload>(
    `/graph/v3/databases/${database}/tables/${table}/edges/get?source=${source}&target=${target}`,
    {
      headers: {
        'Content-Type': 'application/json'
      }
    },
    enableLogging
  );
}

export function count(
  database: string,
  table: string,
  start: any,
  direction: string
) {
  return apiFetch<DataCountPayload>(
    `/graph/v3/databases/${database}/tables/${table}/edges/counts?start=${start}&direction=${direction}`,
    {
      headers: {
        'Content-Type': 'application/json'
      }
    }
  );
}

export function mutate(
  database: string,
  table: string,
  request: EdgeMutation
) {
  return apiFetch<EdgeMutationResponse>(
    `/graph/v3/databases/${database}/tables/${table}/edges`,
    {
      body: JSON.stringify(request),
      method: "POST",
      headers: {
        'Content-Type': 'application/json'
      }
    }
  );
}

export function scan(
  database: string,
  table: string,
  index: string,
  start: any,
  direction: string,
  limit: number | undefined | 25,
  ranges: string | undefined = undefined
) {
  const urlBuilder: string[] = [];
  urlBuilder.push(`/graph/v3/databases/${database}/tables/${table}/edges/scan/${index}?start=${start}&direction=${direction}&limit=${limit}`);
  if (ranges !== undefined) {
    urlBuilder.push(`&ranges=${ranges}`);
  }
  const url = urlBuilder.join("")

  return apiFetch<DataPayload>(
    url,
    {
      headers: {
        'Content-Type': 'application/json'
      }
    }
  );
}

export async function scanUserPosts(postId: string, direction: string = DIRECTION.OUT) {
  return scan(
    DATABASE.SOCIAL,
    TABLE.USER_POSTS,
    INDEX.CREATED_AT_DESC,
    postId,
    direction,
    DEFAULT_LIMIT,
    undefined
  );
}

export async function scanUserFollows(userId: string, direction: string = DIRECTION.OUT) {
  return scan(
    DATABASE.SOCIAL,
    TABLE.USER_FOLLOWS,
    INDEX.CREATED_AT_DESC,
    userId,
    direction,
    DEFAULT_LIMIT,
    undefined
  );
}


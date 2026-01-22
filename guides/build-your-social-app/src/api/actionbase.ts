import {apiFetch} from './client';
import {DATABASE, DIRECTION, TABLE} from "../constants";
import {DatabaseEntity, TableEntity, DataPayload, DataCountPayload, EdgeMutation, EdgeMutationResponse} from './model';

const DEFAULT_LIMIT = 25;
const INDEX = {
  CREATED_AT_DESC: 'created_at_desc',
} as const;

const EMPTY_DATA_PAYLOAD: DataPayload = { edges: [], count: 0, offset: '', hasNext: false };
const EMPTY_COUNT_PAYLOAD: DataCountPayload = { counts: [], count: 0 };

export async function getDatabase(
  name: string,
  enableLogging: boolean = true
): Promise<DatabaseEntity | null> {
  try {
    return await apiFetch<DatabaseEntity>(
      `/graph/v2/service/${name}`,
      {
        headers: {
          'Content-Type': 'application/json'
        }
      },
      enableLogging
    );
  } catch {
    return null;
  }
}

export async function getTable(
  database: string,
  name: string,
  enableLogging: boolean = true
): Promise<TableEntity | null> {
  try {
    return await apiFetch<TableEntity>(
      `/graph/v2/service/${database}/label/${name}`,
      {
        headers: {
          'Content-Type': 'application/json'
        }
      },
      enableLogging
    );
  } catch {
    return null;
  }
}

async function verifyTableExists(database: string, table: string): Promise<boolean> {
  const db = await getDatabase(database, false);
  if (!db) return false;
  const tbl = await getTable(database, table, false);
  return tbl !== null;
}

export async function get(
  database: string,
  table: string,
  source: any,
  target: any,
  enableLogging: boolean = true
): Promise<DataPayload> {
  if (!await verifyTableExists(database, table)) {
    return EMPTY_DATA_PAYLOAD;
  }
  try {
    return await apiFetch<DataPayload>(
      `/graph/v3/databases/${database}/tables/${table}/edges/get?source=${source}&target=${target}`,
      {
        headers: {
          'Content-Type': 'application/json'
        }
      },
      enableLogging
    );
  } catch {
    return EMPTY_DATA_PAYLOAD;
  }
}

export async function count(
  database: string,
  table: string,
  start: any,
  direction: string,
  enableLogging: boolean = true
): Promise<DataCountPayload> {
  if (!await verifyTableExists(database, table)) {
    return EMPTY_COUNT_PAYLOAD;
  }
  try {
    return await apiFetch<DataCountPayload>(
      `/graph/v3/databases/${database}/tables/${table}/edges/counts?start=${start}&direction=${direction}`,
      {
        headers: {
          'Content-Type': 'application/json'
        }
      },
      enableLogging
    );
  } catch {
    return EMPTY_COUNT_PAYLOAD;
  }
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

export async function scan(
  database: string,
  table: string,
  index: string,
  start: any,
  direction: string,
  limit: number | undefined | 25,
  ranges: string | undefined = undefined,
  enableLogging: boolean = true
): Promise<DataPayload> {
  if (!await verifyTableExists(database, table)) {
    return EMPTY_DATA_PAYLOAD;
  }
  try {
    const urlBuilder: string[] = [];
    urlBuilder.push(`/graph/v3/databases/${database}/tables/${table}/edges/scan/${index}?start=${start}&direction=${direction}&limit=${limit}`);
    if (ranges !== undefined) {
      urlBuilder.push(`&ranges=${ranges}`);
    }
    const url = urlBuilder.join("")

    return await apiFetch<DataPayload>(
      url,
      {
        headers: {
          'Content-Type': 'application/json'
        }
      },
      enableLogging
    );
  } catch {
    return EMPTY_DATA_PAYLOAD;
  }
}

export async function scanUserPosts(postId: string, direction: string = DIRECTION.OUT, enableLogging: boolean = true) {
  return scan(
    DATABASE.SOCIAL,
    TABLE.USER_POSTS,
    INDEX.CREATED_AT_DESC,
    postId,
    direction,
    DEFAULT_LIMIT,
    undefined,
    enableLogging
  );
}

export async function scanUserFollows(userId: string, direction: string = DIRECTION.OUT, enableLogging: boolean = true) {
  return scan(
    DATABASE.SOCIAL,
    TABLE.USER_FOLLOWS,
    INDEX.CREATED_AT_DESC,
    userId,
    direction,
    DEFAULT_LIMIT,
    undefined,
    enableLogging
  );
}


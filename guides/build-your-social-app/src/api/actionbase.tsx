import {apiFetch} from './client';

export const get = (
  database: string,
  table: string,
  source: any,
  target: any
) => apiFetch<DataPayload>(
  `/graph/v3/databases/${database}/tables/${table}/edges/get?source=${source}&target=${target}`,
  {
    headers: {
      'Content-Type': 'application/json'
    }
  }
)

export const count = (
  database: string,
  table: string,
  start: any,
  direction: string
) => apiFetch<DataCountPayload>(
  `/graph/v3/databases/${database}/tables/${table}/edges/counts?start=${start}&direction=${direction}`,
  {
    headers: {
      'Content-Type': 'application/json'
    }
  }
)

export const mutate = (
  database: string,
  table: string,
  request: EdgeMutation
) => apiFetch<EdgeMutationResponse>(
  `/graph/v3/databases/${database}/tables/${table}/edges`,
  {
    body: JSON.stringify(request),
    method: "POST",
    headers: {
      'Content-Type': 'application/json'
    }
  }
)

export const scan = (
  database: string,
  table: string,
  index: string,
  start: any,
  direction: string,
  limit: number | undefined | 25,
  ranges: string | undefined = undefined) => {
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

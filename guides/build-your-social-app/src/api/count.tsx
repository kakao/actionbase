import {apiFetch} from "../wrapper/apiClient";

export const count = (
  database: string,
  table: string,
  start: any,
  direction: string
) => apiFetch<DataCountPayload>(
  `/graph/v3/databases/${database}/tables/${table}/edges/counts?start=${start}&direction=${direction}`,
  {
    headers: {
      'Authorization': 'd_gift_front_api-20250916-73b0dc47-4E5E0D64',
      'Content-Type': 'application/json'
    }
  }
)

import {apiFetch} from "../wrapper/apiClient";

export const get = (
  database: string,
  table: string,
  source: any,
  target: any
) => apiFetch<DataPayload>(
  `/graph/v3/databases/${database}/tables/${table}/edges/get?source=${source}&target=${target}`,
  {
    headers: {
      'Authorization': 'd_gift_front_api-20250916-73b0dc47-4E5E0D64',
      'Content-Type': 'application/json'
    }
  }
)

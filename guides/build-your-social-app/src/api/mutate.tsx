import {apiFetch} from "../wrapper/apiClient";

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
      'Authorization': 'd_gift_front_api-20250916-73b0dc47-4E5E0D64',
      'Content-Type': 'application/json'
    }
  }
)

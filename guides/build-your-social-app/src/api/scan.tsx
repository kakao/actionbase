import {apiFetch} from "../wrapper/apiClient";

export const scan = (
  database: string,
  table: string,
  index: string,
  start: any,
  direction: string,
  limit: number | undefined | 25,
  ranges: string | undefined) => {
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
        'Authorization': 'd_gift_front_api-20250916-73b0dc47-4E5E0D64',
        'Content-Type': 'application/json'
      }
    }
  );
}

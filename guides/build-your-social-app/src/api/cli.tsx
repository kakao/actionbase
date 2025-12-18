import {apiFetch} from './client';

export const runCommand = (
  request: CommandRequest
) => apiFetch<CommandResponse>(
  `/api/command`,
  {
    body: JSON.stringify(request),
    method: "POST",
    headers: {
      'Content-Type': 'application/json'
    },
  },
  false
)

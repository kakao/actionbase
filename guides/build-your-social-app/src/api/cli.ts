import {apiFetch} from './client';

export function runCommand(
  request: CommandRequest
) {
  return apiFetch<CommandResponse>(
    `/api/command`,
    {
      body: JSON.stringify(request),
      method: "POST",
      headers: {
        'Content-Type': 'application/json'
      },
    },
    false
  );
}

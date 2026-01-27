import { apiFetch } from './client';
import { CommandRequest, CommandResponse } from './model';

export function run(request: CommandRequest) {
  return apiFetch<CommandResponse>(
    `/api/command`,
    {
      body: JSON.stringify(request),
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    },
    true
  );
}

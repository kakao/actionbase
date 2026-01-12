export function formatDate(timestamp: number | string): string {
  const date = typeof timestamp === 'string' ? new Date(timestamp) : new Date(timestamp);
  return date.toUTCString().split(' ').slice(0, 4).join(' ');
}


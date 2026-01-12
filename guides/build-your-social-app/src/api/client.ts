let apiLogCallback: ((method: string, url: string, success: boolean, status?: number, payload?: any, requestBody?: any) => void) | null = null;

export function setApiLogCallback(callback: (method: string, url: string, success: boolean, status?: number, payload?: any, requestBody?: any) => void) {
  apiLogCallback = callback;
}

export async function apiFetch<T>(url: string, options?: RequestInit, enableLogging: boolean = true): Promise<T> {
  const method = options?.method || 'GET';
  const requestBody = options?.body ? (() => {
    try {
      return JSON.parse(options.body as string);
    } catch {
      return options.body;
    }
  })() : undefined;

  const res = await fetch(url, options);
  const text = await res.text();
  const responseData = (() => {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  })();

  if (apiLogCallback && enableLogging) {
    if (res.ok) {
      apiLogCallback(method, url, res.ok, res.status, responseData, requestBody);
    } else {
      apiLogCallback(method, url, false, res.status, responseData ? responseData : {"message": res.statusText}, requestBody);
    }
  }

  if (!res.ok) {
    const error = new Error(responseData?.message || `${res.status} (${res.statusText})`);
    if (responseData && typeof responseData === 'object' && 'error' in responseData) {
      (error as any).responseData = responseData;
    }
    throw error;
  }

  return responseData as T;
}

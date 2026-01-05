let apiLogCallback: ((method: string, url: string, success: boolean, status?: number, payload?: any, requestBody?: any) => void) | null = null;

export const setApiLogCallback = (callback: (method: string, url: string, success: boolean, status?: number, payload?: any, requestBody?: any) => void) => {
  apiLogCallback = callback;
};

export async function apiFetch<T>(url: string, options?: RequestInit, enableLogging?: boolean): Promise<T> {
  const method = options?.method || 'GET';

  let requestBody: any = undefined;
  if (options?.body) {
    try {
      requestBody = JSON.parse(options.body as string);
    } catch (e) {
      requestBody = options.body;
    }
  }

  try {
    const res = await fetch(url, options);

    let responseData: any;
    const text = await res.text();
    try {
      responseData = JSON.parse(text);
    } catch (e) {
      responseData = text;
    }

    if (apiLogCallback && enableLogging !== false) {
      if (res.ok) {
        apiLogCallback(method, url, true, res.status, responseData, requestBody);
      } else {
        apiLogCallback(method, url, false, res.status, responseData, requestBody);
      }
    }

    if (!res.ok) {
      throw new Error(`HTTP error! status: ${res.status}`);
    }

    return responseData as T;
  } catch (error) {
    if (apiLogCallback && !(error instanceof Error && error.message.includes('HTTP error'))) {
      apiLogCallback(method, url, false, undefined, undefined, requestBody);
    }
    throw error;
  }
}

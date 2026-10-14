export const MAX_IMPORT_BYTES = 200 * 1024 * 1024;

export class ApiError extends Error {
  constructor(status, code, body) {
    super(`${status} ${code}`);
    this.status = status;
    this.code = code;
    this.body = body;
  }

  get isNetwork() { return this.status === 0; }
}

let unauthorizedHandler = () => {};

export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

async function request(method, path, body) {
  const init = { method, credentials: 'same-origin', headers: { Accept: 'application/json' } };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(path, init);
  } catch (error) {
    throw new ApiError(0, 'network', null);
  }
  if (response.status === 204) return null;
  let data = null;
  const text = await response.text().catch(() => '');
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }
  if (!response.ok) {
    if (response.status === 401) unauthorizedHandler();
    throw new ApiError(response.status, data?.error ?? `http_${response.status}`, data);
  }
  return data;
}

export const api = {
  pair: (code, name) => request('POST', 'api/pair', { code, name }),
  bootstrap: () => request('GET', 'api/bootstrap'),
  since: (revision) => request('GET', `api/notes?since=${encodeURIComponent(revision)}`),
  labels: () => request('GET', 'api/labels'),
  createNote: (note) => request('POST', 'api/notes', note),
  patchNote: (id, patch) => request('PATCH', `api/notes/${id}`, patch),
  bulk: (ids, action, extra = {}) => request('POST', 'api/notes/bulk', { ids, action, ...extra }),
  emptyTrash: () => request('POST', 'api/trash/empty'),
  openSession: (id) => request('POST', `api/notes/${id}/session`),
  closeSession: (id) => request('DELETE', `api/notes/${id}/session`),
  createLabel: (name) => request('POST', 'api/labels', { name }),
  renameLabel: (id, name) => request('PATCH', `api/labels/${id}`, { name }),
  deleteLabel: (id) => request('DELETE', `api/labels/${id}`),
};

export function uploadKeepZip(file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', 'api/import/keep');
    xhr.withCredentials = true;
    xhr.setRequestHeader('Content-Type', 'application/zip');
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total);
    };
    xhr.upload.onload = () => onProgress(1);
    xhr.onerror = () => reject(new ApiError(0, 'network', null));
    xhr.onload = () => {
      let data = null;
      try { data = JSON.parse(xhr.responseText); } catch {}
      if (xhr.status >= 200 && xhr.status < 300) return resolve(data);
      if (xhr.status === 401) unauthorizedHandler();
      reject(new ApiError(xhr.status, data?.error ?? `http_${xhr.status}`, data));
    };
    xhr.send(file);
  });
}

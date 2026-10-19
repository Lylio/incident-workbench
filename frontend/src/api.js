export async function api(path, options = {}) {
  const r = await fetch("/api" + path, {
    ...options,
    headers:
      options.body instanceof FormData
        ? {}
        : { "Content-Type": "application/json", ...options.headers },
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok)
    throw Error(data.detail || data.message || `Request failed (${r.status})`);
  return data;
}

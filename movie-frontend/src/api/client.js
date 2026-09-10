const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8083'

const ACCESS_KEY = 'mbs.accessToken'
const REFRESH_KEY = 'mbs.refreshToken'
const USER_KEY = 'mbs.user'

export const tokens = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  user: () => {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) ?? 'null')
    } catch {
      return null
    }
  },
  save: ({ accessToken, refreshToken, username, userId, role }) => {
    localStorage.setItem(ACCESS_KEY, accessToken)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify({ username, userId, role }))
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

/**
 * The one error type the UI reacts to. The backend is precise about status
 * codes and each one means something different to a user, so the status is
 * kept rather than collapsing everything into "request failed".
 */
export class ApiError extends Error {
  constructor(status, body) {
    // The backend's ErrorResponse always carries `message`; Spring's own
    // default error body does not, so fall back to `error`.
    super(body?.message || body?.error || `Request failed (${status})`)
    this.status = status
    this.body = body
  }
}

let refreshing = null

/**
 * Access tokens last 15 minutes, so a 401 mid-session is normal rather than
 * exceptional. One refresh is attempted and the original call replayed.
 *
 * The in-flight promise is shared: a page that fires four requests at once
 * would otherwise start four refreshes, and the last three would present an
 * already-rotated token.
 */
async function refreshAccessToken() {
  const refreshToken = tokens.refresh()
  if (!refreshToken) return null

  refreshing ??= fetch(`${BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
    .then(async (res) => {
      if (!res.ok) return null
      const data = await res.json()
      tokens.save(data)
      return data.accessToken
    })
    .catch(() => null)
    .finally(() => {
      refreshing = null
    })

  return refreshing
}

async function send(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: `Bearer ${token}` }),
    },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  })

  if (res.status === 204) return null

  // 502/503 bodies can be HTML from an infrastructure layer rather than the
  // service's own ErrorResponse, so parsing must not itself throw.
  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text.slice(0, 200) }
    }
  }

  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

export async function api(path, options = {}) {
  const token = tokens.access()

  try {
    return await send(path, { ...options, token })
  } catch (err) {
    // Only a 401 is worth retrying. A 403 means the role is wrong and a new
    // token would say exactly the same thing.
    if (!(err instanceof ApiError) || err.status !== 401 || !tokens.refresh()) {
      throw err
    }

    const fresh = await refreshAccessToken()
    if (!fresh) {
      tokens.clear()
      throw err
    }

    return send(path, { ...options, token: fresh })
  }
}

export const get = (path) => api(path)
export const post = (path, body) => api(path, { method: 'POST', body })
export const put = (path, body) => api(path, { method: 'PUT', body })
export const del = (path) => api(path, { method: 'DELETE' })

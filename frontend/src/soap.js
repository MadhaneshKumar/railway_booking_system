export const API = import.meta.env.VITE_API_URL || 'http://localhost:8080'
export const PAY_MODE = import.meta.env.VITE_PAY_MODE || 'mock'
const NS = 'http://railway.example.com/booking'

// The signed-in user's token. auth.jsx sets it; every request sends it as "Authorization: Bearer ...".
let token = null
let onUnauthenticated = () => {}
export const setToken = (t) => { token = t }
export const setUnauthenticatedHandler = (fn) => { onUnauthenticated = fn }
const authHeader = () => (token ? { Authorization: `Bearer ${token}` } : {})

export class ApiError extends Error {
  constructor(message, code, status) { super(message); this.code = code; this.status = status }
}

/** JSON call to the REST endpoints (/api/auth/..., /api/me/...). */
export async function api(path, { method = 'GET', body } = {}) {
  let res
  try {
    res = await fetch(`${API}${path}`, {
      method,
      headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...authHeader() },
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(`Cannot reach backend at ${API}. Is it running?`, 'NETWORK')
  }
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    if (res.status === 401 && token && data.code === 'UNAUTHENTICATED') onUnauthenticated()
    throw new ApiError(data.message || `HTTP ${res.status}`, data.code, res.status)
  }
  return data
}

/** Escape user text before placing it inside XML. */
export const esc = (v) => String(v ?? '').replace(/[<>&'"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' }[c]))

export async function soap(op, innerXml) {
  const envelope = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:r="${NS}"><soapenv:Body>${innerXml}</soapenv:Body></soapenv:Envelope>`
  let res
  try {
    res = await fetch(`${API}/ws/booking`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/xml; charset=utf-8', SOAPAction: op, ...authHeader() },
      body: envelope,
    })
  } catch {
    throw new Error(`Cannot reach backend at ${API}. Is it running?`)
  }
  const doc = new DOMParser().parseFromString(await res.text(), 'text/xml')
  const fault = doc.getElementsByTagNameNS('*', 'Fault')[0]
  if (fault) {
    const msg = doc.getElementsByTagNameNS('*', 'message')[0]?.textContent
      || doc.getElementsByTagNameNS('*', 'faultstring')[0]?.textContent || 'SOAP fault'
    const code = doc.getElementsByTagNameNS('*', 'code')[0]?.textContent
    if (code === 'UNAUTHENTICATED' && token) onUnauthenticated()      // token expired or no longer valid
    throw new ApiError(msg, code)
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return doc
}

export const text = (n, tag) => n.getElementsByTagNameNS('*', tag)[0]?.textContent
export const all = (n, tag) => [...n.getElementsByTagNameNS('*', tag)]

export async function mockPay(kind, id) {
  const res = await fetch(`${API}/api/mock-pay/${kind}/${id}`, { method: 'POST' })
  if (!res.ok) throw new Error(`Mock payment failed (HTTP ${res.status})`)
  return res.text()
}

export async function getMenu(station) {
  const res = await fetch(`${API}/api/food/menu/${encodeURIComponent(station)}`)
  if (!res.ok) throw new Error(`No menu for ${station}`)
  return res.json()
}

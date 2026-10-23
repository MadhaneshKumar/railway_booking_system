import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react'
import { api, setToken, setUnauthenticatedHandler } from './soap'
import { Card, Btn, Input, Notice } from './ui'

const KEY = 'railway.session'
const Ctx = createContext(null)
export const useAuth = () => useContext(Ctx)

const load = () => { try { return JSON.parse(localStorage.getItem(KEY)) } catch { return null } }
const save = (s) => { try { s ? localStorage.setItem(KEY, JSON.stringify(s)) : localStorage.removeItem(KEY) } catch { /* storage blocked: stay signed in for this page only */ } }

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => { const s = load(); if (s?.token) setToken(s.token); return s })
  const [dialog, setDialog] = useState(false)
  const [expired, setExpired] = useState(false)

  const apply = useCallback((s) => { setToken(s?.token || null); save(s); setSession(s); setExpired(false) }, [])
  const signOut = useCallback(() => apply(null), [apply])

  useEffect(() => {
    setUnauthenticatedHandler(() => { apply(null); setExpired(true); setDialog(true) })
    return () => setUnauthenticatedHandler(() => {})
  }, [apply])

  // On load, confirm the saved token is still good (the server may have restarted with a new signing key).
  useEffect(() => { if (session?.token) api('/api/auth/me').catch(() => {}) }, [])   // eslint-disable-line react-hooks/exhaustive-deps

  const value = useMemo(() => ({
    user: session ? { userId: session.userId, name: session.name } : null,
    signIn: async (userId, password) => { apply(await api('/api/auth/login', { method: 'POST', body: { userId, password } })); setDialog(false) },
    register: async (f) => { apply(await api('/api/auth/register', { method: 'POST', body: f })); setDialog(false) },
    signOut,
    showSignIn: () => setDialog(true),
  }), [session, apply, signOut])

  return (
    <Ctx.Provider value={value}>
      {children}
      {dialog && !session && (
        <div className="fixed inset-0 z-50 bg-night/60 flex items-start sm:items-center justify-center p-4 overflow-y-auto" role="dialog" aria-modal="true" aria-label="Sign in"
          onClick={(e) => { if (e.target === e.currentTarget) setDialog(false) }}>
          <div className="w-full max-w-md">
            <AuthCard expired={expired} onClose={() => setDialog(false)} />
          </div>
        </div>
      )}
    </Ctx.Provider>
  )
}

export function AuthCard({ expired, onClose }) {
  const { signIn, register } = useAuth()
  const [mode, setMode] = useState('signin')
  const [f, setF] = useState({ userId: '', password: '', name: '', email: '', phone: '' })
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.value }))
  const reg = mode === 'register'

  const submit = async (e) => {
    e.preventDefault(); setErr(''); setBusy(true)
    try { reg ? await register(f) : await signIn(f.userId, f.password) }
    catch (x) { setErr(x.message || String(x)) }
    finally { setBusy(false) }
  }
  const ok = f.userId.trim().length >= 3 && f.password.length >= (reg ? 8 : 1)

  return (
    <Card title={reg ? 'Create your account' : 'Sign in'}>
      <form onSubmit={submit} className="space-y-3">
        {expired && <Notice kind="info">Your session ended. Please sign in again.</Notice>}
        <Input label="User ID" autoComplete="username" autoFocus value={f.userId} onChange={set('userId')}
          placeholder="letters, digits, underscore" />
        <Input label={reg ? 'Password (8 or more characters)' : 'Password'} type="password" value={f.password} onChange={set('password')}
          autoComplete={reg ? 'new-password' : 'current-password'} />
        {reg && (
          <>
            <Input label="Name (optional)" autoComplete="name" value={f.name} onChange={set('name')} />
            <div className="grid grid-cols-2 gap-3">
              <Input label="Email (optional)" type="email" autoComplete="email" value={f.email} onChange={set('email')} />
              <Input label="Phone (optional)" type="tel" autoComplete="tel" value={f.phone} onChange={set('phone')} />
            </div>
            <p className="text-xs text-steel">Email and phone are only used to send your ticket and journey alerts.</p>
          </>
        )}
        {err && <Notice>{err}</Notice>}
        <div className="flex items-center gap-3 pt-1">
          <Btn type="submit" disabled={!ok || busy}>{busy ? 'Please wait…' : reg ? 'Create account' : 'Sign in'}</Btn>
          {onClose && <Btn type="button" kind="ghost" onClick={onClose}>Not now</Btn>}
          <button type="button" className="ml-auto text-sm font-semibold text-ink underline underline-offset-2"
            onClick={() => { setMode(reg ? 'signin' : 'register'); setErr('') }}>
            {reg ? 'I already have an account' : 'Create an account'}
          </button>
        </div>
      </form>
    </Card>
  )
}

/** Shows its children when signed in, otherwise a prompt to sign in. */
export function Gate({ children, what = 'this page' }) {
  const { user, showSignIn } = useAuth()
  if (user) return children
  return (
    <Card title="Sign in to continue">
      <p className="text-sm text-steel mb-3">You need to be signed in to use {what}. Your bookings stay private to your account.</p>
      <Btn onClick={showSignIn}>Sign in or create an account</Btn>
    </Card>
  )
}

export function AccountChip() {
  const { user, signOut, showSignIn } = useAuth()
  return user ? (
    <div className="flex items-center gap-3 text-sm">
      <span className="text-white/80">Signed in as <b className="text-white">{user.name || user.userId}</b></span>
      <button onClick={signOut} className="rounded-lg ring-1 ring-white/40 text-white px-3 py-1 font-semibold hover:bg-white/10">Sign out</button>
    </div>
  ) : (
    <button onClick={showSignIn} className="rounded-lg bg-signal text-night px-3 py-1.5 text-sm font-semibold hover:brightness-95">Sign in</button>
  )
}

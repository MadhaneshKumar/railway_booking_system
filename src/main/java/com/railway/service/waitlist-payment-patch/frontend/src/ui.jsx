import { useState, useEffect } from 'react'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useStripe, useElements } from '@stripe/react-stripe-js'
import { PAY_MODE, mockPay } from './soap'

export const Card = ({ title, children, className = '' }) => (
  <section className={`bg-white rounded-2xl p-5 ring-1 ring-line shadow-[0_18px_30px_-24px_rgba(15,42,92,.5)] ${className}`}>
    {title && <h2 className="font-display text-2xl font-semibold text-ink mb-3 leading-none">{title}</h2>}
    {children}
  </section>
)

export const Ticket = ({ main, stub, className = '' }) => (
  <div className={`ticket ${className}`}>
    <div className="p-5 flex flex-col justify-center">{main}</div>
    {stub && <div className="ticket-stub p-5">{stub}</div>}
  </div>
)

export const Btn = ({ kind = 'primary', className = '', ...p }) => {
  const k = {
    primary: 'bg-signal text-night hover:brightness-95 shadow-[0_2px_0_#c99a10]',
    ink: 'bg-ink text-white hover:bg-ink-2',
    ghost: 'ring-1 ring-line text-ink bg-white hover:bg-paper',
    danger: 'bg-maroon text-white hover:brightness-110',
    ok: 'bg-track text-white hover:brightness-110',
  }[kind]
  return <button {...p} className={`px-4 py-2 rounded-lg text-sm font-semibold transition disabled:opacity-45 disabled:cursor-not-allowed active:translate-y-px ${k} ${className}`} />
}

export const Input = ({ label, className = '', ...p }) => (
  <label className="text-xs font-medium text-steel block">
    {label}
    <input {...p} className={`mt-1 block w-full rounded-lg bg-paper ring-1 ring-line focus:ring-2 focus:ring-ink outline-none px-3 py-2 text-sm text-night ${className}`} />
  </label>
)

const TONE = {
  CONFIRMED: 'text-track border-track', PAID: 'text-track border-track', PLACED: 'text-track border-track', ON_TIME: 'text-track border-track', SUBSCRIBED: 'text-track border-track', UPGRADED: 'text-track border-track',
  HELD: 'text-amber-700 border-amber-600', PENDING_PAYMENT: 'text-amber-700 border-amber-600', WAITLISTED: 'text-orange-700 border-orange-600',
  CANCELLED: 'text-maroon border-maroon', DELAYED: 'text-maroon border-maroon',
}
export const Stamp = ({ value }) => (
  <span key={value} className={`stamp inline-block font-display font-bold text-xl tracking-wider uppercase border-[3px] border-double rounded-md px-3 py-0.5 ${TONE[value] || 'text-steel border-steel'}`}>{String(value).replace('_', ' ')}</span>
)
export const Badge = ({ value }) => (
  <span className={`inline-block font-semibold text-xs rounded-full border px-2.5 py-0.5 ${TONE[value] || 'text-steel border-steel'}`}>{String(value).replace('_', ' ')}</span>
)

export const Row = ({ k, children }) => (
  <div className="flex justify-between gap-4 text-sm py-1.5 border-b border-dashed border-line last:border-0">
    <span className="text-steel">{k}</span><span className="font-medium text-right">{children}</span>
  </div>
)

export function Countdown({ until }) {
  const calc = () => Math.max(0, Math.floor((new Date(until) - Date.now()) / 1000))
  const [left, setLeft] = useState(calc)
  useEffect(() => { setLeft(calc()); const t = setInterval(() => setLeft(calc()), 1000); return () => clearInterval(t) }, [until])
  const mm = Math.floor(left / 60), ss = String(left % 60).padStart(2, '0')
  const urgent = left > 0 && left < 120
  return <span className={`font-display text-3xl font-bold tabular-nums align-middle ${urgent ? 'text-maroon' : 'text-ink'}`}>{left > 0 ? `${mm}:${ss}` : 'expired'}</span>
}

const NOTICE = { error: 'bg-maroon/10 text-maroon ring-maroon/25', success: 'bg-track/10 text-track ring-track/25', info: 'bg-ink/5 text-ink ring-ink/15' }
export const Notice = ({ kind = 'error', children }) => (
  <p role={kind === 'error' ? 'alert' : 'status'} className={`rounded-xl px-4 py-3 text-sm font-medium ring-1 ${NOTICE[kind]}`}>{children}</p>
)

let stripePromise
const stripe = () => (stripePromise ||= loadStripe(import.meta.env.VITE_STRIPE_PK))

function StripeForm({ onDone }) {
  const s = useStripe(), el = useElements()
  const [err, setErr] = useState('')
  const submit = async (e) => {
    e.preventDefault()
    const r = await s.confirmPayment({ elements: el, redirect: 'if_required' })
    if (r.error) setErr(r.error.message); else onDone()
  }
  return (
    <form onSubmit={submit} className="space-y-3">
      <PaymentElement />
      {err && <p className="text-maroon text-sm">{err}</p>}
      <Btn kind="ok">Pay now</Btn>
    </form>
  )
}

/** Pays a ticket or food order. kind = 'ticket' | 'food'. */
export function PayBox({ kind, id, secret, onPaid }) {
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  if (PAY_MODE === 'stripe' && !secret) {
    return <p className="text-sm text-steel">Card payment for this booking needs the payment link from your confirmation message.</p>
  }
  if (PAY_MODE === 'stripe' && secret) {
    return <Elements stripe={stripe()} options={{ clientSecret: secret }}><StripeForm onDone={onPaid} /></Elements>
  }
  const go = async () => {
    setBusy(true); setErr('')
    try { await mockPay(kind, id); onPaid() } catch (e) { setErr(e.message) } finally { setBusy(false) }
  }
  return (
    <div className="flex flex-wrap items-center gap-3">
      <Btn kind="ok" onClick={go} disabled={busy}>{busy ? 'Paying…' : 'Pay now (test mode)'}</Btn>
      <span className="text-xs text-steel">No real money moves in test mode.</span>
      {err && <p className="text-maroon text-sm w-full">{err}</p>}
    </div>
  )
}

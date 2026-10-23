import { useState, useEffect, useCallback } from 'react'
import { api } from './soap'
import { Card, Btn, Badge, Notice } from './ui'
import { useAuth } from './auth'

const niceDate = (d) => { try { return new Date(d + 'T00:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) } catch { return d } }

export default function MyBookings({ setPnr, goto, active }) {
  const { user } = useAuth()
  const [rows, setRows] = useState(null)
  const [err, setErr] = useState('')

  const load = useCallback(async () => {
    setErr('')
    try { setRows(await api('/api/me/bookings')) } catch (e) { setErr(e.message) }
  }, [])
  // Refresh whenever the tab is opened or the signed-in user changes.
  useEffect(() => { setRows(null); if (user && active) load() }, [user?.userId, active, load])   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Card title="My bookings">
      <div className="flex items-center justify-between mb-3">
        <p className="text-sm text-steel">Everything booked with <b>{user?.userId}</b>, newest first.</p>
        <Btn kind="ghost" onClick={load}>Refresh</Btn>
      </div>
      {err && <Notice>{err}</Notice>}
      {rows && rows.length === 0 && <p className="text-sm text-steel">No bookings yet. Find a train to make your first one.</p>}
      <ul className="divide-y divide-line">
        {(rows || []).map((b) => (
          <li key={b.pnr} className="py-3 flex flex-wrap items-center gap-x-4 gap-y-2">
            <div className="font-display text-2xl font-bold text-ink tracking-wider">{b.pnr}</div>
            <div className="text-sm text-steel flex-1 min-w-40">
              Train {b.trainNumber}, {b.travelClass}, {niceDate(b.travelDate)}<br />
              {b.passengers} passenger{b.passengers > 1 ? 's' : ''}, ₹{Number(b.amount).toFixed(2)}
            </div>
            <Badge value={b.status} />
            <Btn kind="ink" onClick={() => { setPnr(b.pnr); goto('booking') }}>Open</Btn>
          </li>
        ))}
      </ul>
    </Card>
  )
}

import { useState, useEffect } from 'react'
import { soap, text, esc } from './soap'
import { Card, Ticket, Btn, Input, Badge, Stamp, Countdown, PayBox, Notice } from './ui'
import { QRArt, TrainLine, stationName } from './art'

const q = (tag, v) => `<r:${tag}>${esc(v)}</r:${tag}>`
const money = (n) => `₹${Number(n).toFixed(2)}`

export default function BookingTab({ run, pnr, setPnr }) {
  const [info, setInfo] = useState(null)
  const [refund, setRefund] = useState(null)
  const [result, setResult] = useState(null)
  const [upClass, setUpClass] = useState('3A')
  const [upgrade, setUpgrade] = useState(null)
  const [newDate, setNewDate] = useState(new Date(Date.now() + 5 * 864e5).toISOString().slice(0, 10))
  const [alerts, setAlerts] = useState(null)

  const status = () => run(async () => {
    const d = await soap('getPNRStatus', `<r:GetPNRStatusRequest>${q('pnr', pnr)}</r:GetPNRStatusRequest>`)
    setInfo({ pnr, status: text(d, 'bookingStatus'), train: text(d, 'trainNumber'), date: text(d, 'travelDate'), url: text(d, 'ticketDownloadUrl'), hold: text(d, 'holdExpiresAt') })
    setRefund(null); setResult(null)
  })
  const refundPreview = () => run(async () => {
    const d = await soap('getRefundDetails', `<r:GetRefundDetailsRequest>${q('pnr', pnr)}</r:GetRefundDetailsRequest>`)
    setRefund({ amount: Number(text(d, 'refundableAmount')), charge: Number(text(d, 'cancellationCharges')) })
  })
  const cancel = () => run(async () => {
    if (!window.confirm(`Cancel ticket ${pnr}?`)) return
    const d = await soap('cancelTicket', `<r:CancelTicketRequest>${q('pnr', pnr)}</r:CancelTicketRequest>`)
    setResult(`Ticket cancelled. ${money(text(d, 'refundAmount'))} will be refunded.`); setRefund(null)
    setInfo(i => i && { ...i, status: text(d, 'status') })
  })
  const reschedule = () => run(async () => {
    const d = await soap('rescheduleTicket', `<r:RescheduleTicketRequest>${q('pnr', pnr)}${q('newTravelDate', newDate)}</r:RescheduleTicketRequest>`)
    setResult(`Ticket moved. Your new PNR is ${text(d, 'newPnr')} (${text(d, 'status')}).`)
  })
  const requestUpgrade = () => run(async () => {
    const d = await soap('requestSeatUpgrade', `<r:RequestSeatUpgradeRequest>${q('pnr', pnr)}${q('preferredClass', upClass)}</r:RequestSeatUpgradeRequest>`)
    const id = text(d, 'requestId')
    const s = await soap('getUpgradeStatus', `<r:GetUpgradeStatusRequest>${q('requestId', id)}</r:GetUpgradeStatusRequest>`)
    setUpgrade({ id, status: text(s, 'status'), cls: text(s, 'upgradedClass') })
  })
  const alertsOp = (on) => run(async () => {
    const op = on ? 'subscribeJourneyAlerts' : 'unsubscribeJourneyAlerts'
    const Op = op[0].toUpperCase() + op.slice(1)
    const d = await soap(op, `<r:${Op}Request>${q('pnr', pnr)}</r:${Op}Request>`)
    setAlerts(text(d, 'status'))
  })

  // A waitlisted passenger is promoted in the background; poll so the page notices without a manual refresh.
  useEffect(() => {
    if (info?.status !== 'WAITLISTED') return
    const t = setInterval(() => status(), 15000)
    return () => clearInterval(t)
  }, [info?.status, pnr])

  const live = info && info.status !== 'CANCELLED'
  const pct = refund ? Math.round((refund.amount / ((refund.amount + refund.charge) || 1)) * 100) : 0
  return (
    <div className="space-y-5">
      <Card title="Find your booking">
        <div className="flex gap-3 items-end">
          <div className="flex-1"><Input label="PNR" value={pnr} onChange={e => setPnr(e.target.value.trim())} onKeyDown={e => e.key === 'Enter' && pnr && status()} placeholder="Enter your 10-digit PNR" className="font-display text-2xl tracking-widest" /></div>
          <Btn kind="ink" onClick={status} disabled={!pnr} className="py-3">Check status</Btn>
        </div>
      </Card>

      {result && <Notice kind="success">{result}</Notice>}

      {info && (
        <Ticket
          main={
            <div>
              <div className="text-xs font-medium text-steel">PNR</div>
              <div className="font-display text-5xl font-bold text-ink tracking-[.08em] leading-none">{info.pnr}</div>
              <div className="mt-3 text-sm text-steel">Train <b className="text-night">{info.train}</b> on <b className="text-night">{info.date}</b></div>
              <div className="mt-4 w-full max-w-xs"><TrainLine /></div>
              {info.url && <a className="inline-block mt-4 rounded-lg bg-ink text-white text-sm font-semibold px-4 py-2 hover:bg-ink-2" href={info.url} target="_blank" rel="noreferrer">Download ticket PDF</a>}
              {info.url && <p className="text-xs text-steel mt-2">The download link expires in 15 minutes. Check status again for a fresh one.</p>}
            </div>
          }
          stub={<div className="flex sm:flex-col items-center gap-4 sm:w-40 justify-between"><QRArt value={info.pnr} /><Stamp value={info.status} /></div>}
        />
      )}

      {info?.status === 'WAITLISTED' && <Notice kind="info">You are on the waitlist. This page checks every 15 seconds. When a seat opens you will have time to pay and keep it.</Notice>}

      {info?.status === 'HELD' && (
        <Card title="Seats held for you">
          <p className="text-sm text-steel mb-3">
            {info.hold ? <>Pay within <Countdown until={info.hold} /> or the seats go to the next passenger.</> : 'Pay soon or the seats go to the next passenger.'}
          </p>
          <PayBox kind="ticket" id={info.pnr} onPaid={status} />
        </Card>
      )}

      {live && (
        <div className="grid sm:grid-cols-2 gap-5">
          <Card title="Cancel and refund">
            <Btn kind="ghost" onClick={refundPreview}>Preview refund</Btn>
            {refund && (
              <div className="mt-4">
                <div className="flex h-3.5 rounded-full overflow-hidden bg-line" role="img" aria-label={`Refund ${pct} percent`}>
                  <div style={{ width: `${pct}%` }} className="bg-track" /><div className="bg-maroon flex-1" />
                </div>
                <div className="flex justify-between text-sm mt-2"><span><b className="text-track">{money(refund.amount)}</b> comes back to you</span><span className="text-maroon">{money(refund.charge)} fee</span></div>
              </div>
            )}
            <Btn kind="danger" onClick={cancel} className="mt-4 w-full">Cancel ticket</Btn>
          </Card>

          <Card title="Journey alerts">
            <p className="text-sm text-steel mb-3">Get delays and platform changes by SMS and email.</p>
            <div className="flex gap-2 items-center">
              <Btn kind="ink" onClick={() => alertsOp(true)}>Turn on</Btn>
              <Btn kind="ghost" onClick={() => alertsOp(false)}>Turn off</Btn>
              {alerts && <Badge value={alerts} />}
            </div>
          </Card>

          {info.status === 'CONFIRMED' && (
            <>
              <Card title="Upgrade your seat">
                <div className="flex gap-3 items-end">
                  <label className="text-xs font-medium text-steel flex-1">Class
                    <select className="mt-1 block w-full rounded-lg bg-paper ring-1 ring-line px-3 py-2 text-sm text-night" value={upClass} onChange={e => setUpClass(e.target.value)}>
                      <option value="3A">AC 3 tier (3A)</option><option value="2A">AC 2 tier (2A)</option><option value="1A">First AC (1A)</option>
                    </select>
                  </label>
                  <Btn kind="ink" onClick={requestUpgrade}>Request</Btn>
                </div>
                {upgrade && <p className="text-sm mt-3 flex items-center gap-2">Request <span className="font-mono">{upgrade.id}</span><Badge value={upgrade.status} />{upgrade.cls && <span>now in {upgrade.cls}</span>}</p>}
              </Card>

              <Card title="Change the date">
                <div className="flex gap-3 items-end">
                  <div className="flex-1"><Input label="New travel date" type="date" value={newDate} onChange={e => setNewDate(e.target.value)} /></div>
                  <Btn kind="ink" onClick={reschedule}>Reschedule</Btn>
                </div>
              </Card>
            </>
          )}
        </div>
      )}
    </div>
  )
}

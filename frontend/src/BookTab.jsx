import { useState } from 'react'
import { useAuth } from './auth'
import { soap, text, all, esc } from './soap'
import { Card, Ticket, Btn, Input, Stamp, PayBox } from './ui'
import { TrainLine, QRArt, duration, stationName } from './art'

const CLASS_NAMES = { SL: 'Sleeper', '3A': 'AC 3 tier', '2A': 'AC 2 tier', '1A': 'First AC', CC: 'Chair car' }
const blank = () => ({ name: '', age: '', gender: 'M' })
const niceDate = (d) => new Date(d + 'T00:00').toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })

function CodeInput({ label, value, onChange }) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-steel">{label}</span>
      <input value={value} onChange={e => onChange(e.target.value.toUpperCase())} maxLength={4} aria-label={label}
        className="block w-[4.2ch] sm:w-[4.4ch] bg-transparent border-b-[3px] border-ink font-display font-bold text-6xl sm:text-7xl text-ink leading-none outline-none focus:border-signal uppercase" />
      <span className="block text-xs text-steel mt-1 h-4">{stationName(value)}</span>
    </label>
  )
}

export default function BookTab({ run, pnr, setPnr, goto }) {
  const { user, showSignIn } = useAuth()
  const [f, setF] = useState({ source: 'MAS', destination: 'SBC', date: new Date(Date.now() + 3 * 864e5).toISOString().slice(0, 10) })
  const [trains, setTrains] = useState(null)
  const [avail, setAvail] = useState({})
  const [sel, setSel] = useState(null)
  const [pax, setPax] = useState([blank()])
  const [booking, setBooking] = useState(null)
  const [paid, setPaid] = useState(false)

  const loadAvail = async (list) => {
    await Promise.allSettled(list.flatMap(t => t.classes.map(async c => {
      const d = await soap('checkSeatAvailability', `<r:CheckSeatAvailabilityRequest><r:trainNumber>${esc(t.number)}</r:trainNumber><r:travelDate>${esc(f.date)}</r:travelDate><r:travelClass>${esc(c)}</r:travelClass></r:CheckSeatAvailabilityRequest>`)
      setAvail(a => ({ ...a, [`${t.number}-${c}`]: { seats: Number(text(d, 'availableSeats')), wl: Number(text(d, 'waitlistCount')) } }))
    })))
  }

  const search = () => run(async () => {
    setSel(null); setBooking(null); setAvail({})
    const d = await soap('searchTrains', `<r:SearchTrainsRequest><r:source>${esc(f.source.trim().toUpperCase())}</r:source><r:destination>${esc(f.destination.trim().toUpperCase())}</r:destination><r:travelDate>${esc(f.date)}</r:travelDate></r:SearchTrainsRequest>`)
    const list = all(d, 'trains').map(t => ({
      number: text(t, 'trainNumber'), name: text(t, 'trainName'), dep: text(t, 'departureTime'), arr: text(t, 'arrivalTime'),
      classes: all(t, 'availableClasses').map(c => c.textContent),
    }))
    setTrains(list)
    loadAvail(list)
  })

  const setP = (i, k, v) => setPax(p => p.map((x, j) => (j === i ? { ...x, [k]: v } : x)))
  const valid = pax.every(p => p.name.trim() && Number(p.age) > 0 && Number(p.age) < 120)

  const book = () => run(async () => {
    const paxXml = pax.map(p => `<r:passengers><r:name>${esc(p.name.trim())}</r:name><r:age>${parseInt(p.age, 10)}</r:age><r:gender>${esc(p.gender)}</r:gender></r:passengers>`).join('')
    const d = await soap('bookTicket', `<r:BookTicketRequest><r:trainNumber>${esc(sel.train.number)}</r:trainNumber><r:travelDate>${esc(f.date)}</r:travelDate><r:travelClass>${esc(sel.cls)}</r:travelClass><r:quotaType>GENERAL</r:quotaType>${paxXml}</r:BookTicketRequest>`)
    const b = {
      pnr: text(d, 'pnr'), status: text(d, 'bookingStatus'), secret: text(d, 'paymentClientSecret'),
      train: sel.train, cls: sel.cls, date: f.date, from: f.source, to: f.destination,
      seats: all(d, 'seatAssignments').map(s => ({ who: text(s, 'passengerName'), coach: text(s, 'coach'), no: text(s, 'seatNumber') })),
    }
    setBooking(b); setPaid(false); setPnr(b.pnr)
  })

  const afterPay = () => run(async () => {
    const d = await soap('getPNRStatus', `<r:GetPNRStatusRequest><r:pnr>${esc(booking.pnr)}</r:pnr></r:GetPNRStatusRequest>`)
    setBooking(b => ({ ...b, status: text(d, 'bookingStatus') })); setPaid(true)
  })

  return (
    <div className="space-y-5">
      <Ticket
        main={
          <div>
            <div className="flex items-end gap-3 sm:gap-5">
              <CodeInput label="From" value={f.source} onChange={v => setF({ ...f, source: v })} />
              <div className="flex-1 pb-8 min-w-10"><TrainLine /></div>
              <CodeInput label="To" value={f.destination} onChange={v => setF({ ...f, destination: v })} />
            </div>
            <p className="text-xs text-steel mt-3">Sample data covers Chennai Central (MAS) to Bengaluru (SBC) via Katpadi (KPD).</p>
          </div>
        }
        stub={
          <div className="space-y-3 sm:w-52">
            <Input label="Travel date" type="date" value={f.date} onChange={e => setF({ ...f, date: e.target.value })} />
            <Btn onClick={search} className="w-full py-2.5">Find trains</Btn>
          </div>
        }
      />

      {trains && trains.length === 0 && <Card>No trains run between {f.source} and {f.destination} on {niceDate(f.date)}. Try another date or the MAS to SBC route.</Card>}

      {trains?.map(t => (
        <Ticket key={t.number}
          main={
            <div>
              <div className="flex items-baseline gap-3 flex-wrap">
                <span className="font-display text-3xl font-bold text-ink leading-none">{t.number}</span>
                <span className="text-lg font-semibold">{t.name}</span>
              </div>
              <div className="mt-4 flex items-center gap-4">
                <div><div className="font-display text-4xl font-semibold leading-none">{t.dep}</div><div className="text-xs text-steel mt-1">{stationName(f.source)}</div></div>
                <div className="flex-1 min-w-12 text-center"><div className="text-xs font-medium text-steel mb-0.5">{duration(t.dep, t.arr)}</div><TrainLine /></div>
                <div className="text-right"><div className="font-display text-4xl font-semibold leading-none">{t.arr}</div><div className="text-xs text-steel mt-1">{stationName(f.destination)}</div></div>
              </div>
              <div className="text-xs text-steel mt-3">{niceDate(f.date)}</div>
            </div>
          }
          stub={
            <div className="grid grid-cols-3 sm:grid-cols-1 gap-2 sm:w-52">
              {t.classes.map(c => {
                const a = avail[`${t.number}-${c}`]
                const on = sel?.train.number === t.number && sel.cls === c
                return (
                  <button key={c} onClick={() => { setSel({ train: t, cls: c }); setBooking(null) }} aria-pressed={on}
                    className={`text-left rounded-xl px-3 py-2 ring-1 transition ${on ? 'bg-ink text-white ring-ink' : 'bg-white ring-line hover:ring-ink'}`}>
                    <span className="flex items-baseline gap-2"><span className="font-display text-2xl font-bold leading-none">{c}</span><span className={`text-xs ${on ? 'text-white/70' : 'text-steel'}`}>{CLASS_NAMES[c]}</span></span>
                    <span className="flex items-center gap-1.5 text-xs mt-1 h-4">
                      {a ? (<><span className={`w-2 h-2 rounded-full ${a.seats > 0 ? 'bg-track' : 'bg-orange-500'}`} />{a.seats > 0 ? `${a.seats} left` : `Waitlist ${a.wl}`}</>) : <span className={on ? 'text-white/50' : 'text-steel/60'}>Checking seats…</span>}
                    </span>
                  </button>
                )
              })}
            </div>
          }
        />
      ))}

      {sel && !booking && (
        <Card title={`Who is travelling on ${sel.train.name}?`}>
          <p className="text-sm text-steel -mt-1 mb-3">{sel.cls} class, {niceDate(f.date)}, {sel.train.dep} to {sel.train.arr}</p>
          <div className="space-y-2">
            {pax.map((p, i) => (
              <div key={i} className="grid grid-cols-12 gap-2 items-end">
                <div className="col-span-12 sm:col-span-5"><Input label={`Passenger ${i + 1}`} value={p.name} onChange={e => setP(i, 'name', e.target.value)} /></div>
                <div className="col-span-4 sm:col-span-2"><Input label="Age" type="number" min="1" max="119" value={p.age} onChange={e => setP(i, 'age', e.target.value)} /></div>
                <label className="col-span-5 sm:col-span-3 text-xs font-medium text-steel">Gender
                  <select className="mt-1 block w-full rounded-lg bg-paper ring-1 ring-line px-3 py-2 text-sm text-night" value={p.gender} onChange={e => setP(i, 'gender', e.target.value)}>
                    <option value="M">Male</option><option value="F">Female</option><option value="O">Other</option>
                  </select>
                </label>
                <div className="col-span-3 sm:col-span-2">{pax.length > 1 && <Btn kind="ghost" className="w-full" onClick={() => setPax(pax.filter((_, j) => j !== i))}>Remove</Btn>}</div>
              </div>
            ))}
          </div>
          <div className="flex flex-wrap gap-3 items-end mt-4">
            {pax.length < 6 && <Btn kind="ghost" onClick={() => setPax([...pax, blank()])}>Add passenger</Btn>}
            {user
              ? <Btn onClick={book} disabled={!valid} className="ml-auto">Hold {pax.length} seat{pax.length > 1 ? 's' : ''}</Btn>
              : <Btn onClick={showSignIn} className="ml-auto">Sign in to book</Btn>}
          </div>
        </Card>
      )}

      {booking && (
        <>
          <Ticket
            main={
              <div>
                <div className="text-xs font-medium text-steel">PNR</div>
                <div className="font-display text-5xl font-bold text-ink tracking-[.08em] leading-none">{booking.pnr}</div>
                <div className="mt-4 flex items-center gap-4">
                  <div className="font-display text-4xl font-bold leading-none">{booking.from}</div>
                  <div className="flex-1 min-w-10"><TrainLine /></div>
                  <div className="font-display text-4xl font-bold leading-none">{booking.to}</div>
                </div>
                <div className="text-sm text-steel mt-2">{booking.train.number} {booking.train.name}, {booking.cls}, {niceDate(booking.date)}</div>
                {booking.seats.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-4">
                    {booking.seats.map((s, i) => (
                      <span key={i} className="inline-flex items-center gap-2 rounded-lg bg-signal/25 ring-1 ring-signal px-2.5 py-1 text-sm">
                        <b className="font-display text-lg leading-none">{s.coach}-{s.no}</b>{s.who}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            }
            stub={<div className="flex sm:flex-col items-center gap-4 sm:w-40 justify-between"><QRArt value={booking.pnr} /><Stamp value={booking.status} /></div>}
          />
          {booking.status === 'HELD' && !paid && (
            <Card title="Pay to confirm">
              <p className="text-sm text-steel mb-3">Your seats are held for a few minutes. Pay before the hold expires.</p>
              <PayBox kind="ticket" id={booking.pnr} secret={booking.secret} onPaid={afterPay} />
            </Card>
          )}
          {booking.status === 'WAITLISTED' && <Card>You are on the waitlist. If a seat opens, your ticket is confirmed automatically and you get a message.</Card>}
          {booking.status === 'CONFIRMED' && (
            <div className="flex flex-wrap gap-2">
              <Btn kind="ink" onClick={() => goto('booking')}>Manage this booking</Btn>
              <Btn kind="ghost" onClick={() => goto('food')}>Order food for the journey</Btn>
            </div>
          )}
        </>
      )}
    </div>
  )
}

import { useState, useEffect, useRef } from 'react'
import { soap, text, all, esc } from './soap'
import { Card, Btn, Input, Badge } from './ui'
import { stationName } from './art'

const q = (tag, v) => `<r:${tag}>${esc(v)}</r:${tag}>`
const norm = (s) => String(s || '').toLowerCase().replace(/ (jn|junction|central|city)\b/g, '').trim()

function Stat({ big, unit, label, tone = '', wide = false }) {
  return (
    <div className={`rounded-2xl bg-white ring-1 ring-line p-4 min-w-0 ${wide ? 'col-span-2 sm:col-span-1' : ''}`}>
      <div className={`font-display text-4xl sm:text-5xl font-bold leading-none ${tone || 'text-ink'}`}>{big}<span className="text-xl font-semibold ml-1">{unit}</span></div>
      <div className="text-xs text-steel mt-2">{label}</div>
    </div>
  )
}

function Route({ stations, nextIdx }) {
  const n = stations.length
  const last = n - 1
  const prev = Math.max(0, nextIdx - 1)
  const trainPct = n < 2 ? 0 : nextIdx <= 0 ? 0 : ((prev + 0.5) / last) * 100
  const donePct = n < 2 ? 0 : (trainPct / 100) * 100
  return (
    <div className="relative px-6 pt-10 pb-2">
      <div className="absolute left-6 right-6 top-[3.6rem] h-1.5 rounded-full bg-line" />
      <div className="absolute left-6 top-[3.6rem] h-1.5 rounded-full bg-signal transition-all duration-700" style={{ width: `calc((100% - 3rem) * ${donePct / 100})` }} />
      <div className="absolute top-[1.1rem] -translate-x-1/2 transition-all duration-700" style={{ left: `calc(1.5rem + (100% - 3rem) * ${trainPct / 100})` }} aria-label="Train position" role="img">
        <svg viewBox="0 0 48 40" width="48" height="40">
          <circle className="pulse-ring" cx="24" cy="30" fill="#FFC72C" />
          <rect x="4" y="6" width="40" height="20" rx="8" fill="#0F2A5C" />
          <rect x="9" y="11" width="9" height="7" rx="2" fill="#FFC72C" /><rect x="21" y="11" width="9" height="7" rx="2" fill="#FFC72C" /><rect x="33" y="11" width="7" height="7" rx="2" fill="#fff" />
          <rect x="4" y="21" width="40" height="3" fill="#FFC72C" />
          <circle cx="14" cy="30" r="3.5" fill="#0A1C40" /><circle cx="34" cy="30" r="3.5" fill="#0A1C40" />
        </svg>
      </div>
      <ol className="relative flex justify-between">
        {stations.map((s, i) => {
          const passed = i < nextIdx
          return (
            <li key={s.code} className="flex flex-col items-center w-24 -mx-6 first:items-start last:items-end">
              <span className={`w-5 h-5 rounded-full ring-4 ring-white ${passed ? 'bg-signal' : i === nextIdx ? 'bg-ink' : 'bg-line'}`} />
              <span className="font-display text-2xl font-bold text-ink mt-2 leading-none">{s.code}</span>
              <span className="text-xs text-steel">{stationName(s.code)}</span>
              <span className="text-xs font-semibold mt-1">{s.arr && s.arr !== '-' ? s.arr : s.dep}</span>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

export default function TrainTab({ run }) {
  const [no, setNo] = useState('12607')
  const [date, setDate] = useState(new Date(Date.now() + 3 * 864e5).toISOString().slice(0, 10))
  const [sched, setSched] = useState(null)
  const [pos, setPos] = useState(null)
  const [live, setLive] = useState(null)
  const [auto, setAuto] = useState(false)
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])

  const load = async () => {
    const [s, p, l] = await Promise.all([
      soap('getTrainSchedule', `<r:GetTrainScheduleRequest>${q('trainNumber', no)}</r:GetTrainScheduleRequest>`),
      soap('trackTrain', `<r:TrackTrainRequest>${q('trainNumber', no)}${q('travelDate', date)}</r:TrackTrainRequest>`),
      soap('getTrainLiveStatus', `<r:GetTrainLiveStatusRequest>${q('trainNumber', no)}</r:GetTrainLiveStatusRequest>`),
    ])
    if (!alive.current) return
    setSched(all(s, 'stations').map(x => ({ code: text(x, 'code'), arr: text(x, 'arrivalTime'), dep: text(x, 'departureTime') })))
    setPos({ loc: text(p, 'currentLocation'), next: text(p, 'nextStation'), delay: Number(text(p, 'delayMinutes')), speed: Number(text(p, 'speed')) })
    setLive({ status: text(l, 'trainStatus'), station: text(l, 'currentStation'), eta: text(l, 'expectedArrival') })
  }
  const go = () => run(load)

  useEffect(() => {
    if (!auto) return
    const t = setInterval(() => run(load), 15000)
    return () => clearInterval(t)
  }, [auto, no, date])

  const nextIdx = sched && pos ? Math.max(0, sched.findIndex(s => norm(stationName(s.code)) === norm(pos.next) || s.code === pos.next)) : 0
  const [lat, lng] = (pos?.loc || '').split(',').map(Number)
  return (
    <div className="space-y-5">
      <Card title="Where is my train?">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 items-end">
          <Input label="Train number" value={no} onChange={e => setNo(e.target.value.trim())} />
          <Input label="Travel date" type="date" value={date} onChange={e => setDate(e.target.value)} />
          <Btn kind="ink" onClick={go} className="py-2.5">Track</Btn>
          <label className="text-sm flex items-center gap-2 pb-2"><input type="checkbox" className="w-4 h-4 accent-[#0F2A5C]" checked={auto} onChange={e => setAuto(e.target.checked)} />Refresh every 15 s</label>
        </div>
      </Card>

      {sched && pos && live && (
        <>
          <Card>
            <div className="flex items-center justify-between flex-wrap gap-2 px-1">
              <div><div className="font-display text-3xl font-bold text-ink leading-none">{no}</div><div className="text-sm text-steel mt-1">At {live.station}, heading to {pos.next}</div></div>
              <Badge value={live.status} />
            </div>
            <Route stations={sched} nextIdx={nextIdx} />
          </Card>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <Stat big={pos.delay} unit="min" label={pos.delay > 0 ? 'Running late' : 'On time'} tone={pos.delay > 0 ? 'text-maroon' : 'text-track'} />
            <Stat big={Number.isFinite(pos.speed) ? pos.speed.toFixed(0) : '–'} unit="km/h" label="Current speed" />
            <Stat big={live.eta} label="Expected arrival" wide />
          </div>

          {Number.isFinite(lat) && Number.isFinite(lng) && (
            <a className="inline-block text-sm font-semibold text-ink underline underline-offset-4" target="_blank" rel="noreferrer" href={`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=11/${lat}/${lng}`}>See the train on a map ({lat.toFixed(3)}, {lng.toFixed(3)})</a>
          )}
        </>
      )}
    </div>
  )
}

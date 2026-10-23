/* Illustrations and small drawing helpers. */

export const STATIONS = { MAS: 'Chennai Central', KPD: 'Katpadi Jn', SBC: 'KSR Bengaluru' }
export const stationName = (c) => STATIONS[String(c || '').toUpperCase()] || c || ''
export const stationCodeByName = (n) => Object.keys(STATIONS).find(k => STATIONS[k].toLowerCase().includes(String(n || '').toLowerCase().replace(/ jn$| junction$/, '')) && n) || null

export function duration(dep, arr) {
  const m = (s) => { const [h, mi] = String(s).split(':').map(Number); return h * 60 + mi }
  if (!dep || !arr || isNaN(m(dep)) || isNaN(m(arr))) return ''
  let d = m(arr) - m(dep); if (d < 0) d += 1440
  return `${Math.floor(d / 60)}h ${String(d % 60).padStart(2, '0')}m`
}

const SKYLINE = [[0, 70, 46], [52, 110, 38], [96, 84, 52], [154, 128, 34], [196, 76, 48], [250, 104, 40], [560, 92, 44], [610, 70, 36], [652, 118, 46], [780, 88, 40], [826, 124, 52], [884, 80, 44], [930, 100, 40]]

function Coach({ x }) {
  return (
    <g transform={`translate(${x},0)`}>
      <rect x="0" y="126" width="166" height="46" rx="8" fill="#F2F6FC" />
      <rect x="0" y="152" width="166" height="7" fill="#FFC72C" />
      {[0, 1, 2, 3, 4, 5].map(i => <rect key={i} x={13 + i * 25} y="136" width="17" height="12" rx="3" fill="#274B8F" />)}
      <rect x="10" y="172" width="146" height="6" rx="3" fill="#0A1C40" />
      {[30, 50, 116, 136].map(c => <circle key={c} cx={c} cy="184" r="6" fill="#0A1C40" />)}
    </g>
  )
}

export function Hero() {
  return (
    <header className="relative overflow-hidden text-white" style={{ background: "linear-gradient(#0F2A5C 0%, #0F2A5C 30%, #2551A3 100%)" }}>
      <div className="max-w-4xl mx-auto px-4 pt-9 pb-2 relative z-10">
        <h1 className="font-display font-bold text-5xl sm:text-7xl leading-[.92] tracking-tight">Where are you<br />headed today?</h1>
        <p className="mt-3 text-white/75 max-w-md">Find a train, hold your seat, pay, and follow the journey from one place.</p>
      </div>
      <svg viewBox="0 0 960 220" preserveAspectRatio="xMaxYMax slice" className="block w-full h-[190px] sm:h-[220px] -mt-4" aria-hidden="true" style={{ WebkitMaskImage: "linear-gradient(transparent 0, #000 38%)", maskImage: "linear-gradient(transparent 0, #000 38%)" }}>
        <defs><radialGradient id="halo"><stop offset="0" stopColor="#FFC72C" stopOpacity=".38" /><stop offset="1" stopColor="#FFC72C" stopOpacity="0" /></radialGradient></defs>
        <circle cx="830" cy="118" r="96" fill="url(#halo)" />
        <circle cx="830" cy="118" r="34" fill="#FFC72C" />
        <g fill="#1A3C80">
          {SKYLINE.map(([x, h, w], i) => <rect key={i} x={x} y={190 - h} width={w} height={h} />)}
          <path d="M360 190 V104 Q360 78 392 78 Q424 78 424 104 V190 Z" />
          <rect x="388" y="52" width="8" height="26" />
        </g>
        <g>
          <rect x="468" y="62" width="40" height="128" fill="#17397A" />
          <path d="M464 62 L488 36 L512 62 Z" fill="#17397A" />
          <circle cx="488" cy="88" r="12" fill="#FFC72C" />
          <path d="M488 88 V80 M488 88 H495" stroke="#0F2A5C" strokeWidth="2" strokeLinecap="round" />
        </g>
        <rect y="190" width="960" height="30" fill="#0A1C40" />
        <rect y="190" width="960" height="3" fill="#7F93B8" />
        <line x1="0" y1="204" x2="960" y2="204" stroke="#16306A" strokeWidth="8" strokeDasharray="6 16" />
        <g className="train">
          <g transform="translate(120,0)">
            <Coach x={0} /><Coach x={172} /><Coach x={344} />
            <g transform="translate(516,0)">
              <path d="M0 126 H118 Q158 126 172 158 V172 H0 Z" fill="#F2F6FC" />
              <rect x="0" y="152" width="172" height="7" fill="#FFC72C" />
              <path d="M104 134 H122 Q142 134 150 152 H104 Z" fill="#274B8F" />
              {[0, 1, 2].map(i => <rect key={i} x={13 + i * 25} y="136" width="17" height="12" rx="3" fill="#274B8F" />)}
              <rect x="10" y="172" width="150" height="6" rx="3" fill="#0A1C40" />
              {[30, 52, 118, 140].map(c => <circle key={c} cx={c} cy="184" r="6" fill="#0A1C40" />)}
              <circle cx="166" cy="164" r="3.5" fill="#FFC72C" />
            </g>
          </g>
        </g>
      </svg>
    </header>
  )
}

/** Line with a small train between two stops. */
export function TrainLine({ className = '' }) {
  return (
    <svg viewBox="0 0 200 16" className={`w-full h-4 ${className}`} preserveAspectRatio="none" aria-hidden="true">
      <circle cx="6" cy="8" r="4.5" fill="#0F2A5C" />
      <circle cx="194" cy="8" r="4.5" fill="#fff" stroke="#0F2A5C" strokeWidth="2.5" />
      <line x1="12" y1="8" x2="188" y2="8" stroke="#0F2A5C" strokeWidth="2" strokeDasharray="2 5" strokeLinecap="round" />
      <rect x="86" y="2" width="28" height="12" rx="5" fill="#FFC72C" stroke="#0F2A5C" strokeWidth="1.5" />
      <rect x="92" y="5" width="5" height="4" rx="1" fill="#0F2A5C" /><rect x="101" y="5" width="5" height="4" rx="1" fill="#0F2A5C" />
    </svg>
  )
}

/** Decorative QR-style code, deterministic per text (not scannable). */
export function QRArt({ value = '', size = 92 }) {
  let h = 2166136261
  for (const ch of String(value)) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0 }
  const rnd = () => { h ^= h << 13; h >>>= 0; h ^= h >>> 17; h ^= h << 5; h >>>= 0; return h / 4294967296 }
  const N = 21, cells = []
  const finder = (x, y) => (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13)
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (!finder(x, y) && rnd() > 0.52) cells.push(<rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" />)
  const Finder = ({ x, y }) => (<g transform={`translate(${x},${y})`}><rect width="7" height="7" /><rect x="1" y="1" width="5" height="5" fill="#fff" /><rect x="2" y="2" width="3" height="3" /></g>)
  return (
    <svg viewBox="-1 -1 23 23" width={size} height={size} fill="#0F2A5C" role="img" aria-label="Ticket code">
      <rect x="-1" y="-1" width="23" height="23" fill="#fff" />
      {cells}<Finder x={0} y={0} /><Finder x={14} y={0} /><Finder x={0} y={14} />
    </svg>
  )
}

export const Icon = {
  search: <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" /></svg>,
  ticket: <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"><path d="M3 8a2 2 0 002-2h14a2 2 0 002 2v2a2 2 0 000 4v2a2 2 0 00-2 2H5a2 2 0 00-2-2v-2a2 2 0 000-4z" /><path d="M14 6v12" strokeDasharray="2 3" /></svg>,
  food: <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M7 3v8M4 3v5a3 3 0 006 0V3M7 11v10M17 21V3c-2.5 1.5-3.5 4-3.5 8h3.5" /></svg>,
  pin: <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"><path d="M12 21s7-6.2 7-11.5A7 7 0 005 9.5C5 14.8 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.5" /></svg>,
}

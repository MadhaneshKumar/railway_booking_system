import { useState, useEffect } from 'react'
import BookTab from './BookTab'
import BookingTab from './BookingTab'
import FoodTab from './FoodTab'
import TrainTab from './TrainTab'
import { Hero, Icon } from './art'
import { Notice } from './ui'
import MyBookings from './MyBookings'
import { AuthProvider, Gate, AccountChip, useAuth } from './auth'

const TABS = [['book', 'Find trains', Icon.search], ['mine', 'My bookings', Icon.ticket], ['booking', 'Ticket', Icon.ticket], ['food', 'Food', Icon.food], ['train', 'Track train', Icon.pin]]

export default function App() { return <AuthProvider><Shell /></AuthProvider> }

function Shell() {
  const { user } = useAuth()
  const [tab, setTab] = useState('book')
  const [pnr, setPnr] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  // Every backend call goes through run(): shared loading bar + error banner.
  const run = async (fn) => {
    setError(''); setBusy(true)
    try { await fn() } catch (e) { setError(e.message || String(e)) } finally { setBusy(false) }
  }
  useEffect(() => { if (!user) { setPnr(''); setError('') } }, [user?.userId])   // eslint-disable-line react-hooks/exhaustive-deps
  const props = { run, pnr, setPnr, goto: setTab }

  return (
    <div className="min-h-screen pb-16">
      {busy && <div className="fixed top-0 inset-x-0 h-1 bg-signal z-50 animate-pulse" />}
      <div className="relative">
        <Hero />
        <div className="absolute top-3 right-4 z-20"><AccountChip /></div>
      </div>
      <div className="max-w-4xl mx-auto px-4">
        <nav className="relative z-20 -mt-6 flex gap-1 bg-white rounded-2xl p-1.5 ring-1 ring-line shadow-[0_18px_30px_-22px_rgba(15,42,92,.6)] overflow-x-auto">
          {TABS.map(([k, label, icon]) => (
            <button key={k} onClick={() => { setTab(k); setError('') }} aria-label={label} aria-current={tab === k ? 'page' : undefined}
              className={`flex-1 flex items-center justify-center gap-2 px-3 py-2.5 rounded-xl text-sm font-semibold whitespace-nowrap transition ${tab === k ? 'bg-ink text-white' : 'text-steel hover:bg-paper hover:text-ink'}`}>
              {icon}<span className={tab === k ? '' : 'hidden sm:inline'}>{label}</span>
            </button>
          ))}
        </nav>
        <main className="mt-5 space-y-5">
          {error && <Notice>{error}</Notice>}
          {/* Tabs stay mounted so form state survives switching. */}
          <div className={tab === 'book' ? '' : 'hidden'}><BookTab {...props} /></div>
          <div className={tab === 'mine' ? '' : 'hidden'}><Gate what="your bookings"><MyBookings {...props} active={tab === 'mine'} /></Gate></div>
          <div className={tab === 'booking' ? '' : 'hidden'}><Gate what="ticket details"><BookingTab {...props} /></Gate></div>
          <div className={tab === 'food' ? '' : 'hidden'}><Gate what="food ordering"><FoodTab {...props} /></Gate></div>
          <div className={tab === 'train' ? '' : 'hidden'}><TrainTab {...props} /></div>
        </main>
      </div>
    </div>
  )
}

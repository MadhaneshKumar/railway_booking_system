import { useState, useEffect } from 'react'
import { soap, text, esc, getMenu } from './soap'
import { Card, Btn, Input, Stamp, PayBox } from './ui'
import { stationName } from './art'

const q = (tag, v) => `<r:${tag}>${esc(v)}</r:${tag}>`
const money = (n) => `₹${Number(n).toFixed(0)}`
const EMOJI = { VEG: '🍛', BIR: '🍗', SND: '🥪' }
const emojiFor = (id) => EMOJI[String(id).slice(0, 3)] || '🍽️'
const STEPS = ['Payment', 'Order placed', 'Delivered to seat']
const stepOf = (s) => ({ PENDING_PAYMENT: 0, PAID: 1, PLACED: 1, PREPARING: 1, DELIVERED: 2 }[s] ?? 1)

export default function FoodTab({ run, pnr, setPnr }) {
  const [station, setStation] = useState('KPD')
  const [menu, setMenu] = useState([])
  const [qty, setQty] = useState({})
  const [order, setOrder] = useState(null)
  const [track, setTrack] = useState(null)
  const [msg, setMsg] = useState('')

  useEffect(() => {
    let off = false
    getMenu(station).then(m => { if (!off) setMenu(m.items || []) }).catch(() => { if (!off) setMenu([]) })
    return () => { off = true }
  }, [station])

  const items = menu.filter(i => qty[i.itemId] > 0)
  const total = items.reduce((s, i) => s + i.price * qty[i.itemId], 0)
  const status = track?.status || order?.status

  const place = () => run(async () => {
    const xml = items.map(i => `<r:foodItems>${q('itemId', i.itemId)}${q('quantity', qty[i.itemId])}</r:foodItems>`).join('')
    const d = await soap('orderFood', `<r:OrderFoodRequest>${q('pnr', pnr)}${q('stationCode', station)}${xml}</r:OrderFoodRequest>`)
    setOrder({ id: text(d, 'orderId'), total: text(d, 'totalAmount'), status: text(d, 'status'), secret: text(d, 'paymentClientSecret') })
    setTrack(null); setMsg('')
  })
  const refresh = () => run(async () => {
    const d = await soap('getFoodOrderStatus', `<r:GetFoodOrderStatusRequest>${q('orderId', order.id)}</r:GetFoodOrderStatusRequest>`)
    setTrack({ status: text(d, 'orderStatus'), station: text(d, 'deliveryStation'), eta: text(d, 'estimatedDeliveryTime') })
  })
  const cancel = () => run(async () => {
    if (!window.confirm('Cancel this food order?')) return
    const d = await soap('cancelFoodOrder', `<r:CancelFoodOrderRequest>${q('orderId', order.id)}</r:CancelFoodOrderRequest>`)
    setMsg(`Order cancelled. ${money(text(d, 'refundAmount'))} will be refunded.`)
    setTrack(t => ({ ...(t || {}), status: text(d, 'orderStatus') }))
    setOrder(o => ({ ...o, status: 'CANCELLED' }))
  })
  const afterPay = async () => { setOrder(o => ({ ...o, status: 'PAID' })); await refresh() }

  return (
    <div className="space-y-5">
      <Card title="Hot meals at your seat">
        <div className="grid grid-cols-2 gap-3">
          <Input label="PNR of a confirmed ticket" value={pnr} onChange={e => setPnr(e.target.value.trim())} />
          <div>
            <Input label="Delivery station" value={station} maxLength={4} onChange={e => setStation(e.target.value.trim().toUpperCase())} />
            <p className="text-xs text-steel mt-1 h-4">{stationName(station)}</p>
          </div>
        </div>

        <div className="mt-4 grid sm:grid-cols-3 gap-3">
          {menu.length === 0 && <p className="text-sm text-steel sm:col-span-3">No menu at this station. Try MAS, KPD or SBC.</p>}
          {menu.map(i => {
            const n = qty[i.itemId] || 0
            return (
              <div key={i.itemId} className={`rounded-2xl p-4 ring-1 transition ${n ? 'ring-2 ring-ink bg-ink/[.03]' : 'ring-line bg-white'}`}>
                <div className="text-4xl leading-none" aria-hidden="true">{emojiFor(i.itemId)}</div>
                <div className="font-semibold mt-2">{i.name}</div>
                <div className="font-display text-2xl font-semibold text-ink">{money(i.price)}</div>
                <div className="flex items-center gap-2 mt-3">
                  <Btn kind="ghost" aria-label={`Remove one ${i.name}`} className="!px-3" onClick={() => setQty(s => ({ ...s, [i.itemId]: Math.max(0, n - 1) }))}>−</Btn>
                  <span className="w-6 text-center font-display text-2xl font-semibold">{n}</span>
                  <Btn kind="ghost" aria-label={`Add one ${i.name}`} className="!px-3" onClick={() => setQty(s => ({ ...s, [i.itemId]: n + 1 }))}>+</Btn>
                </div>
              </div>
            )
          })}
        </div>

        <div className="flex justify-between items-center mt-5 pt-4 border-t border-dashed border-line">
          <span className="text-steel">Total <b className="font-display text-3xl text-ink ml-1">{money(total)}</b></span>
          <Btn onClick={place} disabled={!pnr || items.length === 0}>Place order</Btn>
        </div>
      </Card>

      {msg && <p role="status" className="rounded-xl px-4 py-3 text-sm font-medium bg-track/10 text-track ring-1 ring-track/25">{msg}</p>}

      {order && (
        <Card title={`Order ${order.id}`}>
          {status !== 'CANCELLED' ? (
            <ol className="flex items-start mb-4">
              {STEPS.map((s, i) => {
                const done = i <= stepOf(status)
                return (
                  <li key={s} className="flex-1 relative text-center">
                    {i > 0 && <span className={`absolute top-3.5 right-1/2 w-full h-1 -z-0 ${done ? 'bg-track' : 'bg-line'}`} />}
                    <span className={`relative z-10 mx-auto grid place-items-center w-7 h-7 rounded-full text-xs font-bold ${done ? 'bg-track text-white' : 'bg-line text-steel'}`}>{done ? '✓' : i + 1}</span>
                    <span className={`block text-xs mt-1.5 ${done ? 'font-semibold text-night' : 'text-steel'}`}>{s}</span>
                  </li>
                )
              })}
            </ol>
          ) : <div className="mb-4"><Stamp value="CANCELLED" /></div>}
          <div className="flex justify-between text-sm"><span className="text-steel">Amount</span><b>{money(order.total)}</b></div>
          {track?.eta && <div className="flex justify-between text-sm mt-1"><span className="text-steel">Arrives at {stationName(track.station)}</span><b>{track.eta}</b></div>}
          <div className="mt-4">
            {order.status === 'PENDING_PAYMENT' && <PayBox kind="food" id={order.id} secret={order.secret} onPaid={afterPay} />}
            {order.status !== 'PENDING_PAYMENT' && order.status !== 'CANCELLED' && (
              <div className="flex gap-2"><Btn kind="ink" onClick={refresh}>Refresh status</Btn><Btn kind="danger" onClick={cancel}>Cancel order</Btn></div>
            )}
          </div>
        </Card>
      )}
    </div>
  )
}

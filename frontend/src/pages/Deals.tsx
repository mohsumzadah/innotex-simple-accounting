import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { NewDeal } from '../components/NewDeal'
import { QuickRetail } from '../components/QuickRetail'
import { PayStatusBadge } from '../components/Payments'
import { Badge, Empty, ErrorBox, Loading, PageHead } from '../components/ui'
import { get, type Deal, type Stage } from '../lib/api'
import { fmtDate, money, num, STAGE, STAGES, TERMS } from '../lib/format'
import { useAsync } from '../lib/useAsync'

export function StageBadge({ stage }: { stage: Stage }) {
  const tone = stage === 'DONE' ? 'ok' : stage === 'NEW' ? 'muted' : 'info'
  return <Badge tone={tone}>{STAGE[stage]}</Badge>
}

/** Lövhə sütunları: bir neçə mərhələ bir sütunda birləşə bilər */
const COLUMNS: { title: string; stages: Stage[] }[] = [
  { title: 'Yeni', stages: ['NEW'] },
  { title: 'Müqavilə', stages: ['CONTRACT', 'PROTOCOL'] },
  { title: 'Avans / Ödəniş gözlənilir', stages: ['ADVANCE_INVOICE', 'PAID'] },
  { title: 'İcra', stages: ['IN_PROGRESS'] },
  { title: 'Akt', stages: ['ACT'] },
  { title: 'Qaimə', stages: ['INVOICE'] },
  { title: 'Tamamlandı', stages: ['DONE'] },
]

const VIEW_KEY = 'sade.dealsView'
function loadView(): 'table' | 'board' {
  try {
    return localStorage.getItem(VIEW_KEY) === 'board' ? 'board' : 'table'
  } catch {
    return 'table'
  }
}

/** Mərhələyə keçiddən bəri keçən gün sayı */
function daysIn(since: string | null | undefined): number {
  if (!since) return 0
  const [y, m, d] = since.slice(0, 10).split('-').map(Number)
  const t = new Date()
  return Math.max(0, Math.round((Date.UTC(t.getFullYear(), t.getMonth(), t.getDate()) - Date.UTC(y, m - 1, d)) / 86400000))
}

const lc = (s: string | null | undefined) => (s ?? '').toLocaleLowerCase('az')

function PayChip({ d }: { d: Deal }) {
  const paidUp = num(d.remaining) <= 0 && num(d.price) > 0
  return <Badge tone={paidUp ? 'ok' : num(d.paid) > 0 ? 'info' : 'warn'}>{paidUp ? `ödənilib ${money(d.paid)}` : `qalıq ${money(d.remaining)}`}</Badge>
}

export function Deals() {
  const nav = useNavigate()
  const [params, setParams] = useSearchParams()
  const stage = params.get('stage') ?? ''
  const { data, error, loading } = useAsync(() => get<Deal[]>('/deals?includeCancelled=true'), [])
  const [creating, setCreating] = useState(false)
  const [quick, setQuick] = useState(false)
  const [view, setViewState] = useState<'table' | 'board'>(loadView)
  const [q, setQ] = useState('')
  const [year, setYear] = useState('')
  const [showCancelled, setShowCancelled] = useState(false)
  const setView = (v: 'table' | 'board') => {
    setViewState(v)
    try {
      localStorage.setItem(VIEW_KEY, v)
    } catch {
      /* brauzer saxlamağa icazə vermir */
    }
  }

  const live = data?.filter((d) => !d.cancelled)
  const cancelledCount = (data?.length ?? 0) - (live?.length ?? 0)
  const counts: Record<string, number> = {}
  live?.forEach((d) => (counts[d.stage] = (counts[d.stage] ?? 0) + 1))
  const awaiting = params.get('awaiting') === '1'
  const years = [...new Set(data?.map((d) => d.saleDate.slice(0, 4)) ?? [])].sort().reverse()
  const needle = lc(q.trim())
  const rows = data?.filter((d) =>
    (!d.cancelled || (showCancelled && view === 'table')) && (view === 'board' || !stage || d.stage === stage) && (!awaiting || d.awaitingPayment) && (!year || d.saleDate.startsWith(year))
    && (!needle || lc(d.customerName).includes(needle) || lc(d.contractNo).includes(needle) || lc(d.product).includes(needle)))
  const awaitingCount = live?.filter((d) => d.awaitingPayment).length ?? 0
  const sum = (f: (d: Deal) => string) => rows?.filter((d) => !d.cancelled).reduce((s, d) => s + num(f(d)), 0) ?? 0
  const totals = { price: sum((d) => d.price), paid: sum((d) => d.paid), remaining: sum((d) => d.remaining) }

  return (
    <>
      <PageHead title="Satışlar" sub="Lisenziya satışları və mərhələləri">
        <button className="btn ghost" onClick={() => setQuick(true)}>Sürətli satış</button>
        <button className="btn" onClick={() => setCreating(true)}>+ Yeni satış</button>
      </PageHead>
      <ErrorBox error={error} />
      {view === 'table' && (
        <div className="kanban">
          {STAGES.map((s) => (
            <button key={s} className={stage === s ? 'on' : ''} onClick={() => { const p = new URLSearchParams(params); if (stage === s) p.delete('stage'); else p.set('stage', s); setParams(p) }}>
              <div className="c">{counts[s] ?? 0}</div>
              <div className="l">{STAGE[s]}</div>
            </button>
          ))}
        </div>
      )}
      <div className="filters actions" style={{ marginBottom: 12 }}>
        <div className="tabs" style={{ marginBottom: 0 }}>
          <button className={view === 'table' ? 'on' : ''} onClick={() => setView('table')}>Cədvəl</button>
          <button className={view === 'board' ? 'on' : ''} onClick={() => setView('board')}>Lövhə</button>
        </div>
        <input type="search" placeholder="Axtar: kontragent, müqavilə №, məhsul" value={q} onChange={(e) => setQ(e.target.value)} style={{ minWidth: 260 }} />
        <select value={year} onChange={(e) => setYear(e.target.value)}>
          <option value="">Bütün illər</option>
          {years.map((y) => <option key={y} value={y}>{y}</option>)}
        </select>
        <button className={`btn sm ${awaiting ? '' : 'ghost'}`} onClick={() => { const p = new URLSearchParams(params); if (awaiting) p.delete('awaiting'); else p.set('awaiting', '1'); setParams(p) }}>
          Ödəniş gözlənilir ({awaitingCount})
        </button>
        {view === 'table' && (
          <label className="check"><input type="checkbox" checked={showCancelled} onChange={(e) => setShowCancelled(e.target.checked)} /> Ləğv olunanları göstər ({cancelledCount})</label>
        )}
      </div>
      {loading && !data ? <Loading /> : !rows?.length ? (
        <Empty>{stage || q || year || awaiting ? 'Bu filtrə uyğun satış yoxdur.' : 'Hələ satış yoxdur.'}</Empty>
      ) : view === 'table' ? (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Satış tarixi</th><th>Kontragent</th><th>Məhsul</th><th>Mərhələ</th><th>Müqavilə №</th><th>Ödəniş şərti</th><th className="n">Qiymət</th><th className="n">Ödənilib / Qalıq</th><th>Ödəniş</th></tr></thead>
            <tbody>
              {rows.map((d) => (
                <tr key={d.id} className="click" style={d.cancelled ? { opacity: 0.5 } : undefined} onClick={() => nav(`/deals/${d.id}`)}>
                  <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(d.saleDate)}</td>
                  <td><b>{d.customerName}</b>{d.cancelled && <> <Badge tone="bad">ləğv olunub</Badge></>}</td>
                  <td>{d.product}{d.productCode && <span className="muted"> · {d.productCode}</span>}</td>
                  <td><StageBadge stage={d.stage} /></td>
                  <td>{d.contractNo ?? '—'}</td>
                  <td>{TERMS[d.paymentTerms]}{d.paymentTerms === 'PARTIAL' && <span className="muted"> · {Number(d.advancePercent)}%</span>}</td>
                  <td className="n">{money(d.price)}</td>
                  <td className="n" style={{ whiteSpace: 'nowrap' }}>{money(d.paid)} / {money(d.remaining)}</td>
                  <td><PayStatusBadge status={d.paymentStatus} />{d.awaitingPayment && <div className="muted">gözlənilir</div>}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={6}><b>Cəmi ({rows.length} satış)</b></td>
                <td className="n"><b>{money(totals.price)}</b></td>
                <td className="n" style={{ whiteSpace: 'nowrap' }}><b>{money(totals.paid)} / {money(totals.remaining)}</b></td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      ) : (
        <>
          <div className="board">
            {COLUMNS.map((col) => {
              const items = rows.filter((d) => col.stages.includes(d.stage))
              return (
                <div key={col.title} className="board-col">
                  <div className="board-head">{col.title} <span className="muted">{items.length}</span></div>
                  {items.map((d) => (
                    <button key={d.id} className="board-card" onClick={() => nav(`/deals/${d.id}`)}>
                      <b>{d.customerName}</b>
                      <div className="muted">{d.product}</div>
                      <div className="row">
                        <span>{money(d.price)}</span>
                        <PayChip d={d} />
                      </div>
                      <div className="muted">
                        {col.stages.length > 1 && <>{STAGE[d.stage]} · </>}{daysIn(d.stageSince)} gün bu mərhələdə
                      </div>
                    </button>
                  ))}
                </div>
              )
            })}
          </div>
          <div className="card" style={{ marginTop: 12 }}>
            <b>Cəmi ({rows.length} satış):</b> qiymət {money(totals.price)} · ödənilib {money(totals.paid)} · qalıq {money(totals.remaining)}
          </div>
        </>
      )}
      {creating && <NewDeal onClose={() => setCreating(false)} />}
      {quick && <QuickRetail onClose={() => setQuick(false)} />}
    </>
  )
}

import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CustomerForm } from '../components/CustomerForm'
import { BANK, COMPANY, ENTITY, INDIVIDUAL, INDIVIDUAL_BANK, KIND, type Key } from '../lib/counterparty'
import { NewDeal } from '../components/NewDeal'
import { QuickRetail } from '../components/QuickRetail'
import { PayStatusBadge } from '../components/Payments'
import { Badge, Copy, Empty, ErrorBox, Loading, Modal, PageHead } from '../components/ui'
import { del, get, type Counterparty, type Deal } from '../lib/api'
import { fmtDate, money } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { StageBadge } from './Deals'

function Detail({ c, deals, onClose, onNewDeal, onQuick }: { c: Counterparty; deals: Deal[]; onClose: () => void; onNewDeal: () => void; onQuick: () => void }) {
  const row = ([k, l]: [Key, string]) => {
    const v = k === 'kind' ? KIND[c.kind] : (c[k] as string | null)
    return v ? <tr key={k}><td className="muted" style={{ width: 170 }}>{l}</td><td>{k === 'kind' ? v : <Copy text={v} />}</td></tr> : null
  }
  return (
    <Modal title={c.name} onClose={onClose}>
      <div className="actions" style={{ justifyContent: 'space-between' }}>
        <h4 style={{ margin: 0 }}>Satışlar ({deals.length})</h4>
        <div className="actions">
          {c.entityType === 'INDIVIDUAL' && <button className="btn sm" onClick={onQuick}>Sürətli satış</button>}
          <button className={`btn sm${c.entityType === 'INDIVIDUAL' ? ' ghost' : ''}`} onClick={onNewDeal}>+ Yeni satış</button>
        </div>
      </div>
      {!deals.length ? <p className="muted">Bu kontragentin satışı yoxdur.</p> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Tarix</th><th>Məhsul</th><th>Mərhələ</th><th className="n">Qiymət</th><th className="n">Ödənilib / Qalıq</th><th /></tr></thead>
            <tbody>
              {deals.map((d) => (
                <tr key={d.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(d.saleDate)}</td>
                  <td><Link to={`/deals/${d.id}`}>{d.product}</Link>{d.contractNo && <span className="muted"> · {d.contractNo}</span>}</td>
                  <td><StageBadge stage={d.stage} /></td>
                  <td className="n">{money(d.price)}</td>
                  <td className="n" style={{ whiteSpace: 'nowrap' }}>{money(d.paid)} / {money(d.remaining)}</td>
                  <td><PayStatusBadge status={d.paymentStatus} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <h4>{ENTITY[c.entityType]} <Badge tone="muted">{KIND[c.kind]}</Badge></h4>
      <table><tbody>{(c.entityType === 'INDIVIDUAL' ? INDIVIDUAL : COMPANY).map(row)}</tbody></table>
      {(c.entityType !== 'INDIVIDUAL' || c.iban || c.cardNumber) && (
        <>
          <h4>{c.entityType === 'INDIVIDUAL' ? 'Bank / kart' : 'Bank rekvizitləri'}</h4>
          <table><tbody>{(c.entityType === 'INDIVIDUAL' ? INDIVIDUAL_BANK : BANK).map(row)}</tbody></table>
        </>
      )}
      {c.note && (<><h4>Qeyd</h4><p>{c.note}</p></>)}
    </Modal>
  )
}

export function Customers() {
  const { data, error, loading, reload } = useAsync(() => get<Counterparty[]>('/customers'), [])
  const dealsQ = useAsync(() => get<Deal[]>('/deals'), [])
  const [edit, setEdit] = useState<Counterparty | 'new'>()
  const [view, setView] = useState<Counterparty>()
  const [sale, setSale] = useState<number>()
  const [quick, setQuick] = useState<number | 'new'>()
  const [err, setErr] = useState<unknown>()
  const [flt, setFlt] = useState<'ALL' | 'LEGAL' | 'INDIVIDUAL'>('ALL')

  const byCustomer = new Map<number, Deal[]>()
  dealsQ.data?.forEach((d) => byCustomer.set(d.customerId, [...(byCustomer.get(d.customerId) ?? []), d]))

  async function remove(c: Counterparty) {
    if (!confirm(`"${c.name}" silinsin?`)) return
    try {
      await del(`/customers/${c.id}`)
      reload()
    } catch (e) {
      setErr(e)
    }
  }

  return (
    <>
      <PageHead title="Kontragentlər" sub="Müştəri və təchizatçılar: müqavilə, akt və ödənişlərdə istifadə olunan rekvizitlər">
        <button className="btn" onClick={() => setEdit('new')}>+ Yeni kontragent</button>
      </PageHead>
      <ErrorBox error={error ?? err} />
      {loading && !data ? <Loading /> : !data?.length ? (
        <Empty>Hələ kontragent yoxdur.</Empty>
      ) : (
        <>
        <div className="tabs">
          {([['ALL', 'Hamısı'], ['LEGAL', 'Hüquqi'], ['INDIVIDUAL', 'Fiziki']] as const).map(([v, t]) => <button key={v} className={flt === v ? 'on' : ''} onClick={() => setFlt(v)}>{t}</button>)}
        </div>
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Ad</th><th>Növ</th><th>VÖEN / FİN</th><th>Direktor</th><th>Satışlar</th><th>Telefon</th><th /></tr></thead>
            <tbody>
              {data.filter((c) => flt === 'ALL' || c.entityType === flt).map((c) => {
                const ds = byCustomer.get(c.id) ?? []
                const open = ds.filter((d) => d.stage !== 'DONE').length
                const remaining = ds.reduce((s, d) => s + Number(d.remaining), 0)
                return (
                  <tr key={c.id} className="click" onClick={() => setView(c)}>
                    <td><b>{c.name}</b></td>
                    <td><Badge tone={c.entityType === 'INDIVIDUAL' ? 'warn' : 'info'}>{c.entityType === 'INDIVIDUAL' ? 'fiziki şəxs' : 'hüquqi şəxs'}</Badge> <Badge tone="muted">{KIND[c.kind]}</Badge></td>
                    <td>{c.voen ?? (c.entityType === 'INDIVIDUAL' ? c.fin : null)}</td>
                    <td>{c.director}</td>
                    <td>{ds.length ? <>{ds.length}{open > 0 && <span className="muted"> · açıq {open}</span>}{remaining > 0 && <span className="muted"> · qalıq {money(remaining)}</span>}</> : <span className="muted">—</span>}</td>
                    <td>{c.phone}</td>
                    <td className="n">
                      <div className="actions" style={{ justifyContent: 'flex-end' }} onClick={(e) => e.stopPropagation()}>
                        {c.entityType === 'INDIVIDUAL' && <button className="btn sm" onClick={() => setQuick(c.id)}>Sürətli satış</button>}
                        <button className="btn sm" onClick={() => setSale(c.id)}>Yeni satış</button>
                        <button className="btn ghost sm" onClick={() => setEdit(c)}>Dəyiş</button>
                        <button className="btn ghost sm" onClick={() => remove(c)}>Sil</button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        </>
      )}
      {view && <Detail c={view} deals={byCustomer.get(view.id) ?? []} onClose={() => setView(undefined)} onNewDeal={() => { setSale(view.id); setView(undefined) }} onQuick={() => { setQuick(view.id); setView(undefined) }} />}
      {quick !== undefined && <QuickRetail customerId={quick === 'new' ? undefined : quick} onClose={() => setQuick(undefined)} />}
      {sale !== undefined && <NewDeal initial={{ customerId: sale }} onClose={() => setSale(undefined)} />}
      {edit && (
        <CustomerForm
          initial={edit === 'new' ? undefined : edit}
          onClose={() => setEdit(undefined)}
          onSaved={() => {
            setEdit(undefined)
            reload()
          }}
        />
      )}
    </>
  )
}

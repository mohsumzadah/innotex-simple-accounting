import { useEffect, useState } from 'react'
import { Badge, Empty, ErrorBox, Field, FileField, FileLink, FormModal, Loading } from './ui'
import { DateInput } from './DateInput'
import { defaultAccount, del, get, post, put, type Account, type Deal, type DealPayments, type PaymentItem, type PaymentKind, type PaymentStatus, type PaymentSuggestion, type PaymentTerms } from '../lib/api'
import { fmtDate, KIND, money, num, PAY_STATUS, TERMS, today } from '../lib/format'
import { useAsync } from '../lib/useAsync'

export function PayStatusBadge({ status }: { status: PaymentStatus }) {
  const tone = status === 'PAID' ? 'ok' : status === 'PARTIAL' ? 'info' : status === 'OVERPAID' ? 'bad' : 'warn'
  return <Badge tone={tone}>{PAY_STATUS[status]}</Badge>
}

/** Avans məbləği (qiymət × faiz / 100) — yalnız göstəriş üçün; backend hesablayır */
const advanceOf = (price: string | number, percent: string | number) => (num(price) * num(percent) / 100).toFixed(2)

/** "Ödəniş şərti" seçimi + PARTIAL üçün avans faizi + hesablanmış avans */
export function TermsFields({ price, terms, percent, onChange }: {
  price: string | number; terms: PaymentTerms; percent: string; onChange: (terms: PaymentTerms, percent: string) => void
}) {
  const p = terms === 'PREPAID' ? '100' : terms === 'POSTPAID' || terms === 'RETAIL' ? '0' : percent
  return (
    <>
      <Field label="Ödəniş şərti">
        <select value={terms} onChange={(e) => onChange(e.target.value as PaymentTerms, percent)}>
          <option value="PREPAID">{TERMS.PREPAID}</option>
          <option value="PARTIAL">{TERMS.PARTIAL}</option>
          <option value="POSTPAID">{TERMS.POSTPAID}</option>
          <option value="RETAIL">{TERMS.RETAIL}</option>
        </select>
      </Field>
      {terms === 'PARTIAL' && (
        <Field label="Avans faizi (%)">
          <input type="number" min="1" max="99" step="0.01" value={percent} required onChange={(e) => onChange(terms, e.target.value)} />
        </Field>
      )}
      {terms !== 'POSTPAID' && terms !== 'RETAIL' && (
        <div className="muted wide" style={{ gridColumn: '1 / -1' }}>
          Avans məbləği: <b>{money(advanceOf(price || 0, p || 0))}</b> (avans qaiməsində dəyişmək olar)
        </div>
      )}
    </>
  )
}

const kindOptions = (
  <>
    <option value="">Avtomatik</option>
    <option value="ADVANCE">{KIND.ADVANCE}</option>
    <option value="FINAL">{KIND.FINAL}</option>
    <option value="OTHER">{KIND.OTHER}</option>
  </>
)

/** Deal səhifəsindəki "Ödənişlər" kartı. Modal düymələri DealView-dadır (Ödəniş addımı da onları açır). */
export function PaymentsCard({ deal, retail, reloadKey, onLink, onNew, onChanged }: {
  deal: Deal; retail?: boolean; reloadKey: number; onLink: () => void; onNew: () => void; onChanged: () => void
}) {
  const pays = useAsync(() => get<DealPayments>(`/deals/${deal.id}/payments`), [deal.id, reloadKey])
  const [edit, setEdit] = useState<PaymentItem>()
  const [err, setErr] = useState<unknown>()
  const p = pays.data
  return (
    <div className="card">
      <div className="head">
        <h3>Ödənişlər</h3>
        <div className="actions">
          <button className="btn sm" onClick={onLink}>Bank ödənişini bağla</button>
          <button className="btn ghost sm" onClick={onNew}>Yeni bank mədaxili yaz və bağla</button>
        </div>
      </div>
      <ErrorBox error={pays.error ?? err} />
      {!p ? <Loading /> : (
        <>
          <div className="grid">
            <div className="card" style={{ marginBottom: 0 }}><div className="k">Qiymət</div><div className="v">{money(p.price)}</div></div>
            <div className="card" style={{ marginBottom: 0 }}><div className="k">Ödənilib</div><div className="v">{money(p.paid)}</div></div>
            <div className="card" style={{ marginBottom: 0 }}><div className="k">Qalıq</div><div className="v">{money(p.remaining)}</div></div>
            <div className="card" style={{ marginBottom: 0 }}><div className="k">Status</div><div style={{ marginTop: 6 }}><PayStatusBadge status={p.status} /></div></div>
          </div>
          {num(p.advanceRequired) > 0 && (
            <p className="muted">Tələb olunan avans: <b>{money(p.advanceRequired)}</b> · avans ödənilib: <b>{money(p.advancePaid)}</b></p>
          )}
          {!p.items.length ? <Empty>Hələ ödəniş bağlanmayıb.</Empty> : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Tarix</th><th>Hesab</th><th>Bank mətni</th><th className="n">Məbləğ</th><th>Növ</th><th>{retail ? 'Çek / tranzaksiya' : 'Ödəniş tapşırığı'}</th><th /></tr></thead>
                <tbody>
                  {p.items.map((i) => (
                    <tr key={i.id}>
                      <td style={{ whiteSpace: 'nowrap' }}>{fmtDate(i.date)}</td>
                      <td>{i.accountName}</td>
                      <td>{i.movementNote}{i.note && <div className="muted">{i.note}</div>}</td>
                      <td className="n">{money(i.amount)}{i.amount !== i.movementAmount && <div className="muted">{money(i.movementAmount)}-dən</div>}</td>
                      <td>{KIND[i.kind]}</td>
                      <td><FileLink id={i.fileId} text={retail ? 'Çeki endir' : 'fayl'} /></td>
                      <td className="n">
                        <div className="actions" style={{ justifyContent: 'flex-end' }}>
                          <button className="btn ghost sm" onClick={() => setEdit(i)}>Dəyiş</button>
                          <button
                            className="btn ghost sm"
                            onClick={async () => {
                              if (!confirm('Bu ödəniş satışdan ayrılsın? Bank hərəkəti qalacaq.')) return
                              try {
                                await del(`/payment-allocations/${i.id}`)
                                onChanged()
                              } catch (e) {
                                setErr(e)
                              }
                            }}
                          >
                            Ayır
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
      {edit && <EditAllocation item={edit} onClose={() => setEdit(undefined)} onSaved={() => { setEdit(undefined); onChanged() }} />}
    </div>
  )
}

function EditAllocation({ item, onClose, onSaved }: { item: PaymentItem; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({ amount: item.amount, kind: item.kind as PaymentKind, fileId: item.fileId, note: item.note ?? '' })
  return (
    <FormModal title="Ödənişi dəyiş" onClose={onClose} onSubmit={async () => { await put(`/payment-allocations/${item.id}`, f); onSaved() }}>
      <div className="form">
        <Field label={`Məbləğ (₼), hərəkət: ${money(item.movementAmount)}`}><input type="number" step="0.01" min="0.01" value={f.amount} required onChange={(e) => setF({ ...f, amount: e.target.value })} /></Field>
        <Field label="Növ">
          <select value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value as PaymentKind })}>
            <option value="ADVANCE">{KIND.ADVANCE}</option><option value="FINAL">{KIND.FINAL}</option><option value="OTHER">{KIND.OTHER}</option>
          </select>
        </Field>
        <FileField label="Ödəniş tapşırığı" fileId={f.fileId} onChange={(id) => setF((x) => ({ ...x, fileId: id }))} />
        <Field label="Qeyd" wide><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
      </div>
    </FormModal>
  )
}

/** "Bank ödənişini bağla": bölüşdürülməmiş mədaxillərdən seçib satışa bağlayır */
export function LinkMovementModal({ deal, onClose, onSaved }: { deal: Deal; onClose: () => void; onSaved: () => void }) {
  const list = useAsync(() => get<PaymentSuggestion[]>(`/deals/${deal.id}/payment-suggestions`), [deal.id])
  const [movId, setMovId] = useState('')
  const [f, setF] = useState({ amount: '', kind: '', fileId: null as number | null, note: '' })
  const mv = list.data?.find((m) => String(m.movementId) === movId)
  function pick(id: string, from = list.data) {
    setMovId(id)
    const m = from?.find((x) => String(x.movementId) === id)
    if (m) setF((x) => ({ ...x, amount: Math.min(num(m.remaining), num(deal.remaining) || num(m.remaining)).toFixed(2) }))
  }
  // ən yaxşı təklif əvvəlcədən seçilir (yalnız seçim, avtomatik bağlanmır)
  useEffect(() => {
    const best = list.data?.[0]
    if (best && best.score > 0 && !movId) pick(String(best.movementId), list.data)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list.data])
  return (
    <FormModal
      title="Bank ödənişini bağla"
      submitText="Bağla"
      onClose={onClose}
      onSubmit={async () => {
        await post(`/deals/${deal.id}/payments`, { movementId: Number(movId), amount: f.amount || undefined, kind: f.kind || undefined, fileId: f.fileId, note: f.note })
        onSaved()
      }}
    >
      {list.loading && !list.data ? <Loading /> : !list.data?.length ? (
        <Empty>Bağlanmamış bank mədaxili yoxdur. "Yeni bank mədaxili yaz və bağla" istifadə edin.</Empty>
      ) : (
        <div className="form">
          <Field label="Bank mədaxili (uyğunluq balına görə sıralanıb; seçmək üçün vurun)" wide>
            <div className="suggest">
              {list.data.map((m) => (
                <label key={m.movementId} className={String(m.movementId) === movId ? 'on' : ''}>
                  <input type="radio" name="movement" required checked={String(m.movementId) === movId} onChange={() => pick(String(m.movementId))} />
                  <span style={{ flex: 1 }}>
                    {fmtDate(m.date)} · {m.accountName} · <b>{money(m.amount)}</b> (qalıq {money(m.remaining)}){m.note ? ` · ${m.note}` : ''}
                    {m.reasons.length > 0 && (
                      <span className="chips-row">
                        {m.reasons.map((r) => <Badge key={r} tone="ok">{r}</Badge>)}
                        <Badge tone="muted">{m.score} bal</Badge>
                      </span>
                    )}
                  </span>
                </label>
              ))}
            </div>
          </Field>
          <Field label={`Bağlanan məbləğ (₼)${mv ? `, hərəkət qalığı ${money(mv.remaining)}` : ''}`}><input type="number" step="0.01" min="0.01" value={f.amount} required onChange={(e) => setF({ ...f, amount: e.target.value })} /></Field>
          <Field label="Növ"><select value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}>{kindOptions}</select></Field>
          <FileField label="Ödəniş tapşırığı" fileId={f.fileId} onChange={(id) => setF((x) => ({ ...x, fileId: id }))} />
          <Field label="Qeyd" wide><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
        </div>
      )}
    </FormModal>
  )
}

/** "Yeni bank mədaxili yaz və bağla": CUSTOMER_PAYMENT hərəkəti + bağlantı bir addımda */
export function NewIncomeModal({ deal, onClose, onSaved }: { deal: Deal; onClose: () => void; onSaved: () => void }) {
  const accounts = useAsync(() => get<Account[]>('/accounts'), [])
  const [f, setF] = useState({ date: today(), accountId: '', amount: deal.remaining, kind: '', fileId: null as number | null, note: '' })
  const accId = f.accountId || String(defaultAccount(accounts.data)?.id ?? '')
  return (
    <FormModal
      title="Yeni bank mədaxili yaz və bağla"
      submitText="Yaz və bağla"
      onClose={onClose}
      onSubmit={async () => {
        await post('/movements', {
          date: f.date, accountId: Number(accId), direction: 'IN', amount: f.amount, purpose: 'CUSTOMER_PAYMENT', note: f.note, fileId: f.fileId,
          allocation: { dealId: deal.id, kind: f.kind || undefined, fileId: f.fileId },
        })
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Hesab">
          <select value={accId} required onChange={(e) => setF({ ...f, accountId: e.target.value })}>
            {accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </Field>
        <Field label="Tarix"><DateInput value={f.date} required onChange={(v) => setF({ ...f, date: v })} /></Field>
        <Field label="Bankdan daxil olan məbləğ (₼)"><input type="number" step="0.01" min="0.01" value={f.amount} required onChange={(e) => setF({ ...f, amount: e.target.value })} /></Field>
        <Field label="Növ"><select value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}>{kindOptions}</select></Field>
        <FileField label="Ödəniş tapşırığı" fileId={f.fileId} onChange={(id) => setF((x) => ({ ...x, fileId: id }))} />
        <Field label="Bank mətni / qeyd" wide><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
      </div>
      <p className="muted">Satışın qalığından artıq hissə bağlanmır, hərəkətdə boş qalır (başqa satışa bağlaya bilərsiniz).</p>
    </FormModal>
  )
}

/** Hesablar səhifəsindən: mədaxili seçilən satışa bağlayır (satış + məbləğ + növ) */
export function AllocateMovementModal({ movementId, remaining, onClose, onSaved }: { movementId: number; remaining: string; onClose: () => void; onSaved: () => void }) {
  const deals = useAsync(() => get<Deal[]>('/deals'), [])
  const [dealId, setDealId] = useState('')
  const [f, setF] = useState({ amount: remaining, kind: '', fileId: null as number | null, note: '' })
  function pick(id: string) {
    setDealId(id)
    const d = deals.data?.find((x) => String(x.id) === id)
    if (d) setF((x) => ({ ...x, amount: Math.min(num(remaining), num(d.remaining) || num(remaining)).toFixed(2) }))
  }
  return (
    <FormModal
      title="Satışa bağla"
      submitText="Bağla"
      onClose={onClose}
      onSubmit={async () => {
        await post(`/deals/${dealId}/payments`, { movementId, amount: f.amount || undefined, kind: f.kind || undefined, fileId: f.fileId, note: f.note })
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Satış" wide>
          <select value={dealId} required onChange={(e) => pick(e.target.value)}>
            <option value="">— seçin —</option>
            {deals.data?.map((d) => (
              <option key={d.id} value={d.id}>{d.customerName} · {d.contractNo ?? `№${d.id}`} · {money(d.price)} (qalıq {money(d.remaining)})</option>
            ))}
          </select>
        </Field>
        <Field label={`Məbləğ (₼), hərəkət qalığı ${money(remaining)}`}><input type="number" step="0.01" min="0.01" value={f.amount} required onChange={(e) => setF({ ...f, amount: e.target.value })} /></Field>
        <Field label="Növ"><select value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value })}>{kindOptions}</select></Field>
        <FileField label="Ödəniş tapşırığı" fileId={f.fileId} onChange={(id) => setF((x) => ({ ...x, fileId: id }))} />
        <Field label="Qeyd" wide><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
      </div>
    </FormModal>
  )
}

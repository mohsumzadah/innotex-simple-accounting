import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DateInput } from './DateInput'
import { ProductSelect } from './ProductSelect'
import { Badge, Empty, Field, FormModal, Loading } from './ui'
import { defaultAccount, get, postForm, type Account, type Counterparty, type Deal, type PaymentSuggestion, type Product } from '../lib/api'
import { fmtDate, money, today } from '../lib/format'
import { useAsync } from '../lib/useAsync'

const ACCEPT = 'image/png,image/jpeg,application/pdf,.png,.jpg,.jpeg,.pdf'
const NEW_BUYER = 'new'

/**
 * "Sürətli satış": fiziki şəxsə lisenziya satışı. Alıcı (mövcud və ya yeni), məhsul, qiymət, ödəniş (mövcud bank mədaxili və ya yeni mədaxil),
 * çek/ekran şəkli/bank tranzaksiyası faylı və "quraşdırıldı" bir pəncərədə, bir sorğu ilə (POST /deals/quick-retail) yazılır.
 */
export function QuickRetail({ customerId, onClose }: { customerId?: number; onClose: () => void }) {
  const nav = useNavigate()
  const customers = useAsync(() => get<Counterparty[]>('/customers'), [])
  const products = useAsync(() => get<Product[]>('/products?active=true'), [])
  const accounts = useAsync(() => get<Account[]>('/accounts'), [])
  const individuals = customers.data?.filter((c) => c.entityType === 'INDIVIDUAL') ?? []
  const [buyer, setBuyer] = useState(customerId ? String(customerId) : '')
  const [nb, setNb] = useState({ name: '', phone: '', email: '', fin: '' })
  const [p, setP] = useState({ productId: null as number | null, product: 'INNOTEX e-Qaimə', price: '', saleDate: today() })
  const [mode, setMode] = useState<'existing' | 'new'>('existing')
  const [movId, setMovId] = useState('')
  const [nm, setNm] = useState({ accountId: '', date: today(), amount: '', note: '' })
  const [file, setFile] = useState<File | null>(null)
  const [installed, setInstalled] = useState(true)
  const [note, setNote] = useState('')

  const isNew = buyer === NEW_BUYER || (!buyer && !individuals.length)
  const buyerName = isNew ? nb.name : (individuals.find((c) => String(c.id) === buyer)?.name ?? '')
  const sug = useAsync(
    () => get<PaymentSuggestion[]>(`/payment-suggestions?amount=${encodeURIComponent(p.price || '')}&date=${p.saleDate}&name=${encodeURIComponent(buyerName)}`),
    [p.price, p.saleDate, buyerName],
  )
  // ən yaxşı təklif əvvəlcədən seçilir (yalnız seçim; yazma "Satışı yaz"a basanda olur)
  useEffect(() => {
    const best = sug.data?.[0]
    if (best && best.score > 0 && !movId) setMovId(String(best.movementId))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sug.data])
  const accId = nm.accountId || String(defaultAccount(accounts.data)?.id ?? '')

  return (
    <FormModal
      title="Sürətli satış (fiziki şəxs)"
      submitText="Satışı yaz"
      onClose={onClose}
      onSubmit={async () => {
        if (mode === 'existing' && !movId) throw new Error('Bank mədaxilini seçin və ya "Yeni mədaxil" yazın')
        const req = {
          customerId: isNew ? undefined : Number(buyer),
          buyer: isNew ? nb : undefined,
          productId: p.productId ?? undefined,
          product: p.product,
          price: p.price,
          saleDate: p.saleDate || undefined,
          movementId: mode === 'existing' ? Number(movId) : undefined,
          movement: mode === 'new' ? { accountId: Number(accId), date: nm.date, amount: nm.amount || p.price, note: nm.note } : undefined,
          installed,
          note,
        }
        const form = new FormData()
        form.append('request', new Blob([JSON.stringify(req)], { type: 'application/json' }))
        if (file) form.append('file', file)
        const d = await postForm<Deal>('/deals/quick-retail', form)
        nav(`/deals/${d.id}`)
      }}
    >
      <div className="form">
        <Field label="Alıcı (fiziki şəxs)" wide>
          <select
            value={isNew ? NEW_BUYER : buyer}
            required
            onChange={(e) => { setBuyer(e.target.value); setMovId('') }}
          >
            <option value="">— seçin —</option>
            {individuals.map((c) => <option key={c.id} value={c.id}>{c.name}{c.phone ? ` · ${c.phone}` : ''}</option>)}
            <option value={NEW_BUYER}>+ Yeni alıcı</option>
          </select>
        </Field>
        {isNew && (
          <>
            <Field label="Ad, soyad"><input value={nb.name} required onChange={(e) => setNb({ ...nb, name: e.target.value })} /></Field>
            <Field label="Telefon"><input value={nb.phone} onChange={(e) => setNb({ ...nb, phone: e.target.value })} /></Field>
            <Field label="E-poçt"><input type="email" value={nb.email} onChange={(e) => setNb({ ...nb, email: e.target.value })} /></Field>
            <Field label="FİN (ixtiyari)"><input value={nb.fin} onChange={(e) => setNb({ ...nb, fin: e.target.value })} /></Field>
          </>
        )}
        <ProductSelect
          products={products.data ?? []}
          productId={p.productId}
          product={p.product}
          onText={(v) => setP({ ...p, product: v })}
          onPick={(x) => setP(x ? { ...p, productId: x.id, product: x.name, price: x.defaultPrice ?? p.price } : { ...p, productId: null })}
        />
        <Field label="Qiymət (₼)"><input type="number" step="0.01" min="0.01" value={p.price} required onChange={(e) => setP({ ...p, price: e.target.value })} /></Field>
        <Field label="Satış tarixi"><DateInput value={p.saleDate} required onChange={(v) => setP({ ...p, saleDate: v })} /></Field>
      </div>

      <h4>Ödəniş</h4>
      <div className="tabs">
        <button type="button" className={mode === 'existing' ? 'on' : ''} onClick={() => setMode('existing')}>Mövcud bank mədaxili</button>
        <button type="button" className={mode === 'new' ? 'on' : ''} onClick={() => setMode('new')}>Yeni mədaxil</button>
      </div>
      {mode === 'existing' ? (
        sug.loading && !sug.data ? <Loading /> : !sug.data?.length ? (
          <Empty>Bağlanmamış bank mədaxili yoxdur. "Yeni mədaxil" seçin.</Empty>
        ) : (
          <div className="suggest">
            {sug.data.map((m) => (
              <label key={m.movementId} className={String(m.movementId) === movId ? 'on' : ''}>
                <input type="radio" name="movement" checked={String(m.movementId) === movId} onChange={() => setMovId(String(m.movementId))} />
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
        )
      ) : (
        <div className="form">
          <Field label="Hesab">
            <select value={accId} required onChange={(e) => setNm({ ...nm, accountId: e.target.value })}>
              {accounts.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
          <Field label="Mədaxil tarixi"><DateInput value={nm.date} required onChange={(v) => setNm({ ...nm, date: v })} /></Field>
          <Field label="Bankdan daxil olan məbləğ (₼)"><input type="number" step="0.01" min="0.01" value={nm.amount} placeholder={p.price} onChange={(e) => setNm({ ...nm, amount: e.target.value })} /></Field>
          <Field label="Bank mətni" wide><input value={nm.note} placeholder="Bank çıxarışındakı təyinat mətni" onChange={(e) => setNm({ ...nm, note: e.target.value })} /></Field>
        </div>
      )}

      <div className="form" style={{ marginTop: 12 }}>
        <Field label="Çek / ekran şəkli / bank tranzaksiyası (PNG, JPEG, PDF)" wide>
          <input type="file" accept={ACCEPT} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </Field>
        <Field label="Qeyd" wide><input value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        <label className="wide" style={{ gridColumn: '1 / -1', display: 'flex', gap: 8, alignItems: 'center' }}>
          <input type="checkbox" checked={installed} onChange={(e) => setInstalled(e.target.checked)} />
          Quraşdırıldı — satışı tamamla
        </label>
      </div>
      <p className="muted">Müqavilə, akt və e-qaimə yoxdur. Satış tamamlananda gəlir həmin tarixdə (satış və ödəniş tarixlərinin gec olanı) hesabata düşür.</p>
    </FormModal>
  )
}

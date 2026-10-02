import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DateInput } from './DateInput'
import { CustomerForm } from './CustomerForm'
import { TermsFields } from './Payments'
import { ProductSelect, QuantityField } from './ProductSelect'
import { Field, FormModal } from './ui'
import { get, post, type Customer, type Deal, type PaymentTerms, type Product } from '../lib/api'
import { scaleByQuantity, today } from '../lib/format'
import { useAsync } from '../lib/useAsync'

/** "Yeni satış" pəncərəsinin əvvəlcədən doldurulan sahələri (kontragentin səhifəsindən və ya "Təkrar satış"dan) */
export interface DealPrefill {
  customerId?: number
  productId?: number | null
  product?: string
  description?: string | null
  price?: string
  computers?: number
  quantity?: number
  paymentTerms?: PaymentTerms
  advancePercent?: string
}

/** Yeni satış: bir pəncərədə kontragent (yenisini də yaratmaq olar), məhsul, ödəniş şərti, tarix və müqavilənin başlanması */
export function NewDeal({ initial, onClose }: { initial?: DealPrefill; onClose: () => void }) {
  const nav = useNavigate()
  const customers = useAsync(() => get<Customer[]>('/customers'), [])
  const products = useAsync(() => get<Product[]>('/products?active=true'), [])
  const [addCustomer, setAddCustomer] = useState(false)
  const [startContract, setStartContract] = useState(true)
  const [f, setF] = useState({
    customerId: initial?.customerId ? String(initial.customerId) : '',
    productId: initial?.productId ?? (null as number | null),
    product: initial?.product ?? 'INNOTEX e-Qaimə',
    description: initial?.description ?? '',
    price: initial?.price ?? '',
    computers: String(initial?.computers ?? 1),
    quantity: String(initial?.quantity ?? 1),
    saleDate: today(),
    paymentTerms: (initial?.paymentTerms ?? 'POSTPAID') as PaymentTerms,
    advancePercent: initial?.advancePercent ? String(Number(initial.advancePercent) || 50) : '50',
  })
  const retail = f.paymentTerms === 'RETAIL'
  // fiziki şəxs seçiləndə ödəniş şərti RETAIL olur (dəyişmək olar); başqa kontragentdə RETAIL qalmır
  const termsFor = (c: Customer | undefined, cur: PaymentTerms): PaymentTerms => (c?.entityType === 'INDIVIDUAL' ? 'RETAIL' : cur === 'RETAIL' ? 'POSTPAID' : cur)
  useEffect(() => {
    if (initial?.paymentTerms || !initial?.customerId) return
    const c = customers.data?.find((x) => x.id === initial.customerId)
    if (c?.entityType === 'INDIVIDUAL') setF((x) => ({ ...x, paymentTerms: 'RETAIL' }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customers.data])
  return (
    <>
      <FormModal
        title="Yeni satış"
        submitText={startContract && !retail ? 'Yarat və müqaviləni hazırla' : 'Yarat'}
        onClose={onClose}
        onSubmit={async () => {
          const d = await post<Deal>('/deals', {
            customerId: Number(f.customerId), productId: f.productId ?? undefined, product: f.product, description: f.description,
            price: f.price, computers: Number(f.computers), quantity: Number(f.quantity), saleDate: f.saleDate || undefined,
            paymentTerms: f.paymentTerms, advancePercent: f.paymentTerms === 'PARTIAL' ? f.advancePercent : undefined,
            startContract: startContract && !retail,
          })
          nav(`/deals/${d.id}`, { state: startContract && !retail ? { open: 'CONTRACT' } : undefined })
        }}
      >
        <div className="form">
          <Field label="Kontragent" wide>
            <div className="actions">
              <select style={{ flex: 1 }} value={f.customerId} required onChange={(e) => setF({ ...f, customerId: e.target.value, paymentTerms: termsFor(customers.data?.find((c) => String(c.id) === e.target.value), f.paymentTerms) })}>
                <option value="">— seçin —</option>
                {customers.data?.map((c) => <option key={c.id} value={c.id}>{c.name}{c.entityType === 'INDIVIDUAL' ? ' (fiziki şəxs)' : ' (hüquqi şəxs)'}</option>)}
              </select>
              <button type="button" className="btn ghost" onClick={() => setAddCustomer(true)}>+ Yeni kontragent</button>
            </div>
          </Field>
          <ProductSelect
            products={products.data ?? []}
            productId={f.productId}
            product={f.product}
            onText={(v) => setF({ ...f, product: v })}
            onPick={(p) => setF(p ? {
              // nomenklaturanın qiyməti və kompüter sayı 1 vahid üçündür: miqdara vurulur
              ...f, productId: p.id, product: p.name,
              price: p.defaultPrice ? scaleByQuantity(p.defaultPrice, 1, 1, Number(f.quantity) || 1).price : f.price,
              computers: p.defaultComputers ? String(p.defaultComputers * (Number(f.quantity) || 1)) : f.computers, description: p.description ?? f.description,
              paymentTerms: f.paymentTerms === 'RETAIL' ? 'RETAIL' : p.defaultPaymentTerms ?? f.paymentTerms,
              advancePercent: p.defaultPaymentTerms === 'PARTIAL' && p.defaultAdvancePercent ? String(Number(p.defaultAdvancePercent)) : f.advancePercent,
            } : { ...f, productId: null })}
          />
          <QuantityField
            quantity={f.quantity}
            unit={products.data?.find((p) => p.id === f.productId)?.unit}
            onChange={(q, from, to) => setF((x) => {
              if (!from || !to) return { ...x, quantity: q }
              const s = scaleByQuantity(x.price, Number(x.computers) || 1, from, to)
              return { ...x, quantity: q, price: s.price, computers: String(s.computers) }
            })}
          />
          <Field label="Qiymət (₼)"><input type="number" step="0.01" min="0" value={f.price} required onChange={(e) => setF({ ...f, price: e.target.value })} /></Field>
          <Field label="Satış tarixi"><DateInput value={f.saleDate} required onChange={(v) => setF({ ...f, saleDate: v })} /></Field>
          <Field label="Kompüter sayı"><input type="number" min="1" value={f.computers} required onChange={(e) => setF({ ...f, computers: e.target.value })} /></Field>
          <TermsFields price={f.price} terms={f.paymentTerms} percent={f.advancePercent} onChange={(t, p) => setF({ ...f, paymentTerms: t, advancePercent: p })} />
          <Field label="Təsvir" wide><textarea rows={2} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
          {!retail ? (
          <label className="wide" style={{ gridColumn: '1 / -1', display: 'flex', gap: 8, alignItems: 'center' }}>
            <input type="checkbox" checked={startContract} onChange={(e) => setStartContract(e.target.checked)} />
            Müqavilə nömrəsi ver və əsas şablondan müqaviləni hazırla
          </label>
          ) : (
            <div className="muted wide" style={{ gridColumn: '1 / -1' }}>Fiziki şəxs satışında müqavilə, akt və e-qaimə yoxdur: ödəniş (çek) alınandan sonra satış tamamlanır.</div>
          )}
        </div>
      </FormModal>
      {addCustomer && (
        <CustomerForm
          onClose={() => setAddCustomer(false)}
          onSaved={(c) => {
            setAddCustomer(false)
            customers.reload()
            setF((x) => ({ ...x, customerId: String(c.id), paymentTerms: termsFor(c, x.paymentTerms) }))
          }}
        />
      )}
    </>
  )
}

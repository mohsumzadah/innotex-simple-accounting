import { useEffect, useRef } from 'react'
import { Field } from './ui'
import { type Product } from '../lib/api'

/** "Məhsul" seçimi: aktiv nomenklatura + "siyahıda yoxdur" (sərbəst mətn). Seçəndə ad, qiymət, kompüter, təsvir doldurulur. */
export function ProductSelect({ products, productId, product, currentName, onPick, onText }: {
  products: Product[]
  productId: number | null
  product: string
  currentName?: string
  onPick: (p: Product | null) => void
  onText: (v: string) => void
}) {
  const known = productId == null || products.some((p) => p.id === productId)
  return (
    <>
      <Field label="Məhsul">
        <select
          value={productId ?? ''}
          onChange={(e) => onPick(products.find((p) => p.id === Number(e.target.value)) ?? null)}
        >
          <option value="">— siyahıda yoxdur —</option>
          {!known && <option value={productId!}>{currentName || product} (passiv)</option>}
          {products.map((p) => <option key={p.id} value={p.id}>{p.name}{p.code ? ` (${p.code})` : ''}</option>)}
        </select>
      </Field>
      <Field label="Məhsulun adı (sənədlərdə)">
        <input value={product} required onChange={(e) => onText(e.target.value)} />
      </Field>
    </>
  )
}

/** "Miqdar" sahəsi: yazılan son etibarlı miqdarı yadda saxlayır ki, sahə müvəqqəti boşalanda da nisbət düzgün hesablansın */
export function QuantityField({ quantity, unit, onChange }: { quantity: string; unit?: string | null; onChange: (q: string, from?: number, to?: number) => void }) {
  const last = useRef(Number(quantity) >= 1 ? Number(quantity) : 1)
  useEffect(() => {
    const n = Number(quantity)
    if (Number.isInteger(n) && n >= 1) last.current = n
  }, [quantity])
  return (
    <Field label={`Miqdar${unit ? ` (${unit})` : ''}`}>
      <input
        type="number"
        min="1"
        step="1"
        value={quantity}
        required
        onChange={(e) => {
          const n = Number(e.target.value)
          if (e.target.value !== '' && Number.isInteger(n) && n >= 1 && n !== last.current) {
            const from = last.current
            last.current = n
            onChange(e.target.value, from, n)
          } else onChange(e.target.value)
        }}
      />
    </Field>
  )
}

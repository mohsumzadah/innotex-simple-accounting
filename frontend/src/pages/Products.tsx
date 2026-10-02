import { useState } from 'react'
import { Badge, Empty, ErrorBox, Field, FormModal, Loading } from '../components/ui'
import { del, get, post, put, type PaymentTerms, type Product } from '../lib/api'
import { TERMS } from '../lib/format'
import { useAsync } from '../lib/useAsync'

export function Products() {
  const list = useAsync(() => get<Product[]>('/products'), [])
  const [edit, setEdit] = useState<Product | 'new'>()
  const [err, setErr] = useState<unknown>()
  const guard = async (fn: () => Promise<unknown>) => {
    setErr(undefined)
    try {
      await fn()
      list.reload()
    } catch (e) {
      setErr(e)
    }
  }
  return (
    <div className="card table-wrap">
      <div className="head">
        <h3>Nomenklatura</h3>
        <button className="btn sm" onClick={() => setEdit('new')}>+ Yeni məhsul</button>
      </div>
      <p className="muted" style={{ marginTop: 0 }}>Məhsul və xidmətlər kataloqu. Satış yaradanda seçəndə ad, qiymət, kompüter sayı və təsvir avtomatik dolur; sənəd şablonlarında <code>{'{{product.*}}'}</code> yer tutucuları ilə istifadə olunur.</p>
      <ErrorBox error={list.error ?? err} />
      {list.loading && !list.data ? <Loading /> : !list.data?.length ? <Empty>Məhsul yoxdur.</Empty> : (
        <table>
          <thead><tr><th>Ad</th><th>Kod</th><th>Ölçü vahidi</th><th className="n">Qiymət</th><th className="n">Kompüter</th><th>Ödəniş şərti</th><th /></tr></thead>
          <tbody>
            {list.data.map((p) => (
              <tr key={p.id} style={p.active ? undefined : { opacity: 0.55 }}>
                <td><b>{p.name}</b> {!p.active && <Badge>passiv</Badge>}</td>
                <td>{p.code}</td>
                <td>{p.unit}</td>
                <td className="n">{p.defaultPrice ?? ''}</td>
                <td className="n">{p.defaultComputers ?? ''}</td>
                <td>{p.defaultPaymentTerms ? <>{TERMS[p.defaultPaymentTerms]}{p.defaultPaymentTerms === 'PARTIAL' && <span className="muted"> · {Number(p.defaultAdvancePercent)}%</span>}</> : ''}</td>
                <td className="n">
                  <div className="actions" style={{ justifyContent: 'flex-end' }}>
                    <button className="btn ghost sm" onClick={() => setEdit(p)}>Dəyiş</button>
                    <button className="btn ghost sm" onClick={() => guard(() => put(`/products/${p.id}`, { ...p, active: !p.active }))}>{p.active ? 'Passiv et' : 'Aktiv et'}</button>
                    <button className="btn ghost sm" onClick={() => confirm(`"${p.name}" silinsin?`) && guard(() => del(`/products/${p.id}`))}>Sil</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {edit && <ProductForm initial={edit === 'new' ? undefined : edit} onClose={() => setEdit(undefined)} onSaved={() => { setEdit(undefined); list.reload() }} />}
    </div>
  )
}

function ProductForm({ initial, onClose, onSaved }: { initial?: Product; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState(() => ({
    name: initial?.name ?? '', code: initial?.code ?? '', unit: initial?.unit ?? 'ədəd', defaultPrice: initial?.defaultPrice ?? '',
    defaultComputers: initial?.defaultComputers ? String(initial.defaultComputers) : '', description: initial?.description ?? '', active: initial?.active ?? true,
    defaultPaymentTerms: (initial?.defaultPaymentTerms ?? '') as PaymentTerms | '',
    defaultAdvancePercent: initial?.defaultAdvancePercent ? String(Number(initial.defaultAdvancePercent) || 50) : '50',
  }))
  return (
    <FormModal
      title={initial ? 'Məhsulu dəyiş' : 'Yeni məhsul'}
      onClose={onClose}
      onSubmit={async () => {
        const body = {
          ...f, defaultPrice: f.defaultPrice === '' ? null : f.defaultPrice,
          defaultComputers: f.defaultComputers === '' ? null : Number(f.defaultComputers),
          defaultPaymentTerms: f.defaultPaymentTerms || null,
          defaultAdvancePercent: f.defaultPaymentTerms === 'PARTIAL' ? f.defaultAdvancePercent : null,
        }
        if (initial) await put(`/products/${initial.id}`, body)
        else await post('/products', body)
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Ad" wide><input value={f.name} required onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Mal/xidmət kodu"><input value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /></Field>
        <Field label="Ölçü vahidi"><input value={f.unit} required onChange={(e) => setF({ ...f, unit: e.target.value })} /></Field>
        <Field label="Default qiymət (₼)"><input type="number" step="0.01" min="0" value={f.defaultPrice} onChange={(e) => setF({ ...f, defaultPrice: e.target.value })} /></Field>
        <Field label="Default kompüter sayı"><input type="number" min="1" value={f.defaultComputers} onChange={(e) => setF({ ...f, defaultComputers: e.target.value })} /></Field>
        <Field label="Default ödəniş şərti">
          <select value={f.defaultPaymentTerms} onChange={(e) => setF({ ...f, defaultPaymentTerms: e.target.value as PaymentTerms | '' })}>
            <option value="">— göstərilməyib —</option>
            <option value="PREPAID">{TERMS.PREPAID}</option>
            <option value="PARTIAL">{TERMS.PARTIAL}</option>
            <option value="POSTPAID">{TERMS.POSTPAID}</option>
          </select>
        </Field>
        {f.defaultPaymentTerms === 'PARTIAL' && (
          <Field label="Default avans faizi (%)"><input type="number" min="1" max="99" step="0.01" value={f.defaultAdvancePercent} required onChange={(e) => setF({ ...f, defaultAdvancePercent: e.target.value })} /></Field>
        )}
        <Field label="Təsvir" wide><textarea rows={3} maxLength={2000} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
        <label className="check wide">
          <input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Aktiv
        </label>
      </div>
    </FormModal>
  )
}


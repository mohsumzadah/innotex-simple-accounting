import { useState } from 'react'
import { Badge, Empty, ErrorBox, Field, FormModal, Loading } from '../components/ui'
import { del, get, post, put, type Account, type ExpenseItem } from '../lib/api'
import { CATEGORIES } from '../lib/format'
import { useAsync } from '../lib/useAsync'

type ItemDraft = {
  name: string; vendor: string; category: string; currency: string; defaultAmount: string; accountId: string
  deductible: boolean; recurrence: 'MONTHLY' | 'NONE'; active: boolean; note: string
}

export function ExpenseItems() {
  const list = useAsync(() => get<ExpenseItem[]>('/expense-items'), [])
  const accounts = useAsync(() => get<Account[]>('/accounts'), [])
  const [edit, setEdit] = useState<ExpenseItem | 'new'>()
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
  const toBody = (i: ExpenseItem, over: Partial<ExpenseItem> = {}) => ({ ...i, ...over })
  return (
    <div className="card table-wrap">
      <div className="head">
        <h3>Xərc maddələri</h3>
        <button className="btn sm" onClick={() => setEdit('new')}>+ Yeni maddə</button>
      </div>
      <p className="muted" style={{ marginTop: 0 }}>Təkrarlanan xərclər (server, abunəlik və s.) üçün şablon. Xərc yazarkən seçəndə sahələr avtomatik dolur; "Hər ay" olanlar ayda yazılmayanda Xərclər səhifəsində xatırladılır.</p>
      <ErrorBox error={list.error ?? err} />
      {list.loading && !list.data ? <Loading /> : !list.data?.length ? <Empty>Xərc maddəsi yoxdur.</Empty> : (
        <table>
          <thead><tr><th>Ad</th><th>Təchizatçı</th><th>Kateqoriya</th><th className="n">Məbləğ</th><th>Hesab</th><th>Təkrar</th><th /></tr></thead>
          <tbody>
            {list.data.map((i) => (
              <tr key={i.id} style={i.active ? undefined : { opacity: 0.55 }}>
                <td><b>{i.name}</b> {!i.active && <Badge>passiv</Badge>} {!i.deductible && <Badge tone="warn">çıxılmır</Badge>}</td>
                <td>{i.vendor}</td>
                <td>{i.category}</td>
                <td className="n">{i.defaultAmount ? `${i.defaultAmount} ${i.currency}` : ''}</td>
                <td>{i.accountName}</td>
                <td>{i.recurrence === 'MONTHLY' ? 'Hər ay' : 'Birdəfəlik'}</td>
                <td className="n">
                  <div className="actions" style={{ justifyContent: 'flex-end' }}>
                    <button className="btn ghost sm" onClick={() => setEdit(i)}>Dəyiş</button>
                    <button className="btn ghost sm" onClick={() => guard(() => put(`/expense-items/${i.id}`, toBody(i, { active: !i.active })))}>{i.active ? 'Passiv et' : 'Aktiv et'}</button>
                    <button className="btn ghost sm" onClick={() => confirm(`"${i.name}" silinsin?`) && guard(() => del(`/expense-items/${i.id}`))}>Sil</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {edit && (
        <ItemForm
          initial={edit === 'new' ? undefined : edit}
          accounts={accounts.data ?? []}
          onClose={() => setEdit(undefined)}
          onSaved={() => { setEdit(undefined); list.reload() }}
        />
      )}
    </div>
  )
}

function ItemForm({ initial, accounts, onClose, onSaved }: { initial?: ExpenseItem; accounts: Account[]; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<ItemDraft>(() => ({
    name: initial?.name ?? '', vendor: initial?.vendor ?? '', category: initial?.category ?? CATEGORIES[0], currency: initial?.currency ?? 'AZN',
    defaultAmount: initial?.defaultAmount ?? '', accountId: initial?.accountId ? String(initial.accountId) : '',
    deductible: initial?.deductible ?? true, recurrence: initial?.recurrence ?? 'MONTHLY', active: initial?.active ?? true, note: initial?.note ?? '',
  }))
  return (
    <FormModal
      title={initial ? 'Maddəni dəyiş' : 'Yeni xərc maddəsi'}
      onClose={onClose}
      onSubmit={async () => {
        const body = { ...f, defaultAmount: f.defaultAmount === '' ? null : f.defaultAmount, accountId: f.accountId ? Number(f.accountId) : null }
        if (initial) await put(`/expense-items/${initial.id}`, body)
        else await post('/expense-items', body)
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Ad"><input value={f.name} required onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Təchizatçı"><input value={f.vendor} onChange={(e) => setF({ ...f, vendor: e.target.value })} /></Field>
        <Field label="Kateqoriya">
          <select value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
            {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
          </select>
        </Field>
        <Field label="Ödənildiyi hesab">
          <select value={f.accountId} onChange={(e) => setF({ ...f, accountId: e.target.value })}>
            <option value="">— seçilməyib —</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </Field>
        <Field label="Adi məbləğ"><input type="number" step="0.01" min="0" value={f.defaultAmount} onChange={(e) => setF({ ...f, defaultAmount: e.target.value })} /></Field>
        <Field label="Valyuta">
          <select value={f.currency} onChange={(e) => setF({ ...f, currency: e.target.value })}>
            <option>AZN</option><option>USD</option><option>EUR</option>
          </select>
        </Field>
        <Field label="Təkrar">
          <select value={f.recurrence} onChange={(e) => setF({ ...f, recurrence: e.target.value as 'MONTHLY' | 'NONE' })}>
            <option value="MONTHLY">Hər ay</option>
            <option value="NONE">Birdəfəlik</option>
          </select>
        </Field>
        <Field label="Qeyd"><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
        <label className="check wide">
          <input type="checkbox" checked={f.deductible} onChange={(e) => setF({ ...f, deductible: e.target.checked })} /> Vergidən çıxılır
        </label>
        <label className="check wide">
          <input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Aktiv
        </label>
      </div>
    </FormModal>
  )
}


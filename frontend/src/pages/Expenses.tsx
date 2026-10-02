import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge, Empty, ErrorBox, Field, FileField, FileLink, FormModal, StatementLink, Loading, PageHead } from '../components/ui'
import { DateInput } from '../components/DateInput'
import { MonthInput } from '../components/MonthInput'
import { defaultAccount, del, get, post, put, qs, type Account, type Expense, type ExpenseItem, type MissingItem, type RunSummary } from '../lib/api'
import { CATEGORIES, fmtDate, fmtMonth, money, monthName, num, thisMonth, today } from '../lib/format'
import { useAsync } from '../lib/useAsync'

type Draft = {
  date: string; docDate: string; vendor: string; category: string; description: string; amount: string; currency: string
  rate: string; amountAzn: string; accountId: string; deductible: boolean; fileId: number | null; note: string; itemId: string
}

/** Maddədən doldurulmuş başlanğıc dəyərlər (Yaz düyməsi və maddə seçimi) */
function fromItem(it: ExpenseItem, accounts: Account[]): Partial<Draft> {
  return {
    itemId: String(it.id), vendor: it.vendor ?? '', category: it.category ?? CATEGORIES[0], currency: it.currency, amount: it.defaultAmount ?? '',
    accountId: it.accountId ? String(it.accountId) : String(defaultAccount(accounts)?.id ?? ''), deductible: it.deductible, rate: '1',
  }
}

function ExpenseForm({ initial, accounts, items, presetItem, presetDate, onClose, onSaved }: {
  initial?: Expense; accounts: Account[]; items: ExpenseItem[]; presetItem?: ExpenseItem; presetDate?: string; onClose: () => void; onSaved: () => void
}) {
  const [f, setF] = useState<Draft>(() => ({
    date: initial?.date ?? presetDate ?? today(), docDate: initial?.docDate ?? presetDate ?? today(), vendor: initial?.vendor ?? '', category: initial?.category ?? CATEGORIES[0],
    description: initial?.description ?? '', amount: initial?.amount ?? '', currency: initial?.currency ?? 'AZN',
    rate: initial?.rate ?? '1', amountAzn: initial?.amountAzn ?? '', accountId: initial?.accountId ? String(initial.accountId) : String(defaultAccount(accounts)?.id ?? ''),
    deductible: initial?.deductible ?? true, fileId: initial?.fileId ?? null, note: initial?.note ?? '', itemId: initial?.itemId ? String(initial.itemId) : '',
    ...(presetItem && !initial ? fromItem(presetItem, accounts) : {}),
  }))
  // AZN məbləğ avtomatik hesablanır, amma əl ilə dəyişdirilə bilər (bankın tutduğu real məbləğ)
  const [manual, setManual] = useState(!!initial)
  const pickItem = (id: string) => {
    const it = items.find((x) => String(x.id) === id)
    if (!it) return upd({ itemId: '' })
    setManual(false)
    upd(fromItem(it, accounts))
  }
  const calc = (amount: string, rate: string) => (amount && rate ? (num(amount) * num(rate)).toFixed(2) : '')
  const upd = (p: Partial<Draft>) =>
    setF((x) => {
      const n = { ...x, ...p }
      if (n.currency === 'AZN') n.rate = '1'
      if (!manual || n.currency === 'AZN') n.amountAzn = calc(n.amount, n.rate)
      return n
    })

  return (
    <FormModal
      title={initial ? 'Xərci dəyiş' : 'Yeni xərc'}
      onClose={onClose}
      onSubmit={async () => {
        const body = { ...f, accountId: f.accountId ? Number(f.accountId) : null, itemId: f.itemId ? Number(f.itemId) : null }
        if (initial) await put(`/expenses/${initial.id}`, body)
        else await post('/expenses', body)
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Xərc maddəsi" wide>
          <select value={f.itemId} onChange={(e) => pickItem(e.target.value)}>
            <option value="">— maddəsiz —</option>
            {items.filter((x) => x.active || String(x.id) === f.itemId).map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
          </select>
        </Field>
        <Field label="Sənəd tarixi (invoys)"><DateInput value={f.docDate} required onChange={(v) => upd({ docDate: v })} /></Field>
        <Field label="Ödəniş tarixi"><DateInput value={f.date} required onChange={(v) => upd({ date: v })} /></Field>
        <Field label="Təchizatçı"><input value={f.vendor} required onChange={(e) => upd({ vendor: e.target.value })} /></Field>
        <Field label="Kateqoriya">
          <select value={f.category} onChange={(e) => upd({ category: e.target.value })}>
            {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
          </select>
        </Field>
        <Field label="Ödənildiyi hesab">
          <select value={f.accountId} required onChange={(e) => upd({ accountId: e.target.value })}>
            <option value="">— seçin —</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </Field>
        <Field label="Məbləğ"><input type="number" step="0.01" min="0" value={f.amount} required onChange={(e) => upd({ amount: e.target.value })} /></Field>
        <Field label="Valyuta">
          <select value={f.currency} onChange={(e) => upd({ currency: e.target.value })}>
            <option>AZN</option><option>USD</option><option>EUR</option>
          </select>
        </Field>
        {f.currency !== 'AZN' && (
          <Field label="Məzənnə"><input type="number" step="0.0001" min="0" value={f.rate} required onChange={(e) => upd({ rate: e.target.value })} /></Field>
        )}
        <Field label="AZN məbləğ (bankın tutduğu real məbləği yaza bilərsiniz)">
          <input type="number" step="0.01" min="0" value={f.amountAzn} required onChange={(e) => { setManual(true); setF({ ...f, amountAzn: e.target.value }) }} />
        </Field>
        <Field label="Təsvir" wide><input value={f.description} onChange={(e) => upd({ description: e.target.value })} /></Field>
        <FileField label="Qəbz / invoys" fileId={f.fileId} onChange={(id) => setF((x) => ({ ...x, fileId: id }))} />
        <Field label="Qeyd"><input value={f.note} onChange={(e) => upd({ note: e.target.value })} /></Field>
        <label className="check wide">
          <input type="checkbox" checked={f.deductible} onChange={(e) => setF({ ...f, deductible: e.target.checked })} /> Vergidən çıxılır (mənfəət vergisi bazasını azaldır)
        </label>
      </div>
    </FormModal>
  )
}

export function Expenses() {
  const [month, setMonth] = useState(thisMonth())
  const [all, setAll] = useState(false)
  const range = () => {
    if (all || !month) return {}
    const [y, m] = month.split('-').map(Number)
    const last = new Date(y, m, 0).getDate()
    return { from: `${month}-01`, to: `${month}-${String(last).padStart(2, '0')}` }
  }
  const list = useAsync(() => get<Expense[]>('/expenses' + qs(range())), [month, all])
  const accounts = useAsync(() => get<Account[]>('/accounts'), [])
  const items = useAsync(() => get<ExpenseItem[]>('/expense-items'), [])
  const missing = useAsync(() => (all || !month ? Promise.resolve([] as MissingItem[]) : get<MissingItem[]>('/expense-items/missing' + qs({ month }))), [month, all])
  const [edit, setEdit] = useState<Expense | 'new'>()
  const [fill, setFill] = useState<{ item: ExpenseItem; date: string }>()
  const startFill = (m: MissingItem) => {
    const item = items.data?.find((x) => x.id === m.itemId)
    if (!item) return
    setFill({ item, date: today().startsWith(month) ? today() : `${month}-01` })
  }
  const reloadAll = () => { list.reload(); missing.reload() }
  const [err, setErr] = useState<unknown>()
  // Maaş xərci (FINAL cədvəllər) hesabatda olduğu kimi burada da görünür; dəyişmək Əmək haqqı bölməsindədir
  const runs = useAsync(() => get<RunSummary[]>('/payroll-runs'), [])
  const payroll = (runs.data ?? []).filter((r) => r.status === 'FINAL' && (all || !month || r.month === month))
  const total = (list.data?.reduce((s, e) => s + num(e.amountAzn), 0) ?? 0) + payroll.reduce((s, r) => s + num(r.totalEmployerCost), 0)

  return (
    <>
      <PageHead title="Xərclər" sub="Xərclər sənəd (invoys) tarixinə görə; ödəniş tarixi ikinci sətirdə">
        <button className="btn" onClick={() => setEdit('new')}>+ Yeni xərc</button>
      </PageHead>
      <div className="filters">
        <MonthInput value={month} disabled={all} onChange={(v) => setMonth(v)} />
        <label className="check"><input type="checkbox" checked={all} onChange={(e) => setAll(e.target.checked)} /> Bütün dövr</label>
      </div>
      <ErrorBox error={list.error ?? err} />
      {!all && !!month && !!missing.data?.length && (
        <div className="card">
          <h3>Bu ay yazılmayıb</h3>
          <table>
            <tbody>
              {missing.data.map((m) => (
                <tr key={m.itemId}>
                  <td><b>{m.name}</b> {m.vendor && <span className="muted">{m.vendor}</span>}</td>
                  <td className="muted">{m.lastDate ? `Son: ${money(m.lastAmountAzn)} (${fmtDate(m.lastDate)})` : 'Hələ yazılmayıb'}</td>
                  <td className="n"><button className="btn sm" onClick={() => startFill(m)}>Yaz</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.loading && !list.data ? <Loading /> : !list.data?.length && !payroll.length ? (
        <Empty>Bu dövrdə xərc yoxdur.</Empty>
      ) : (
        <div className="card table-wrap">
          <table>
            <thead><tr><th>Sənəd tarixi</th><th>Təchizatçı</th><th>Kateqoriya</th><th>Təsvir / qeyd</th><th className="n">Məbləğ</th><th className="n">AZN</th><th>Qəbz</th><th /></tr></thead>
            <tbody>
              {payroll.map((r) => (
                <tr key={'p' + r.id}>
                  <td>{fmtMonth(r.month)}</td>
                  <td><b>Əmək haqqı</b></td>
                  <td>Əmək haqqı və sosial ayırmalar <Badge>maaş cədvəli</Badge></td>
                  <td>{monthName(r.month)}: gross {money(r.totalGross)} + işəgötürən ayırmaları</td>
                  <td className="n" />
                  <td className="n">{money(r.totalEmployerCost)}</td>
                  <td />
                  <td className="n"><Link className="btn ghost sm" to={`/payroll/${r.id}`}>Aç</Link></td>
                </tr>
              ))}
              {(list.data ?? []).map((e) => (
                <tr key={e.id}>
                  <td>{fmtDate(e.docDate)}{e.docDate !== e.date && <div className="muted">ödəniş: {fmtDate(e.date)}</div>}</td>
                  <td><b>{e.vendor}</b> {e.itemId && <Badge tone="info">{items.data?.find((x) => x.id === e.itemId)?.name ?? 'maddə'}</Badge>}</td>
                  <td>{e.category} {!e.deductible && <Badge tone="warn">çıxılmır</Badge>}</td>
                  <td>
                    {e.description}
                    {e.note && <div className="muted" style={{ wordBreak: 'break-all', color: e.note.startsWith('YOXLAYIN') ? 'var(--bad)' : undefined }}>{e.note}</div>}
                  </td>
                  <td className="n">{e.currency === 'AZN' ? '' : `${e.amount} ${e.currency}`}</td>
                  <td className="n">{money(e.amountAzn)}</td>
                  <td><FileLink id={e.fileId} text="Bax" /> <StatementLink id={e.statementId} /></td>
                  <td className="n">
                    <div className="actions" style={{ justifyContent: 'flex-end' }}>
                      <button className="btn ghost sm" onClick={() => setEdit(e)}>Dəyiş</button>
                      <button
                        className="btn ghost sm"
                        onClick={async () => {
                          if (!confirm('Xərc silinsin? Hesabdakı hərəkət də silinəcək.')) return
                          try {
                            await del(`/expenses/${e.id}`)
                            reloadAll()
                          } catch (x) {
                            setErr(x)
                          }
                        }}
                      >
                        Sil
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot><tr><th colSpan={5}>Cəmi</th><th className="n">{money(total)}</th><th colSpan={2} /></tr></tfoot>
          </table>
        </div>
      )}
      {(edit || fill) && accounts.data && items.data && (
        <ExpenseForm
          initial={edit === 'new' ? undefined : edit}
          accounts={accounts.data}
          items={items.data}
          presetItem={fill?.item}
          presetDate={fill?.date}
          onClose={() => { setEdit(undefined); setFill(undefined) }}
          onSaved={() => {
            setEdit(undefined)
            setFill(undefined)
            reloadAll()
          }}
        />
      )}
    </>
  )
}

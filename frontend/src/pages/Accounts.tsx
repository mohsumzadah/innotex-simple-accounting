import { useState, type ReactNode } from 'react'
import { AllocateMovementModal } from '../components/Payments'
import { Badge, ConfirmModal, Copy, Empty, ErrorBox, Field, FileField, FileLink, FormModal, Loading, PageHead, StatementLink } from '../components/ui'
import { DateInput } from '../components/DateInput'
import { StatementsBlock } from '../components/Statements'
import { Link } from 'react-router-dom'
import { defaultAccount, del, get, post, put, qs, type Account, type AccountType, type Direction, type Movement } from '../lib/api'
import { ACCOUNT_TYPE, fmtDate, label, money, num, PURPOSE, today } from '../lib/format'
import { useAsync } from '../lib/useAsync'

const REQ_FIELDS: ['bankName' | 'iban' | 'bankCode' | 'bankVoen' | 'swift' | 'correspondentAccount' | 'cardNumber' | 'note', string][] = [
  ['bankName', 'Bank'], ['iban', 'IBAN'], ['bankCode', 'Bank kodu'], ['bankVoen', 'Bankın VÖEN-i'], ['swift', 'SWIFT'],
  ['correspondentAccount', 'Müxbir hesab'], ['cardNumber', 'Kart nömrəsi'], ['note', 'Qeyd'],
]

function AccountForm({ initial, onClose, onSaved }: { initial?: Account; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    name: initial?.name ?? '', type: (initial?.type ?? 'BANK') as AccountType,
    openingBalance: initial?.openingBalance ?? '0', openingDate: initial?.openingDate ?? today(),
    bankName: initial?.bankName ?? '', iban: initial?.iban ?? '', bankCode: initial?.bankCode ?? '', bankVoen: initial?.bankVoen ?? '',
    swift: initial?.swift ?? '', correspondentAccount: initial?.correspondentAccount ?? '', cardNumber: initial?.cardNumber ?? '', note: initial?.note ?? '',
  })
  return (
    <FormModal
      title={initial ? 'Hesabı dəyiş' : 'Yeni hesab'}
      onClose={onClose}
      onSubmit={async () => {
        const body = { ...f, currency: 'AZN' }
        if (initial) await put(`/accounts/${initial.id}`, body)
        else await post('/accounts', body)
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Ad" wide><input value={f.name} required onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Növ">
          <select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value as AccountType })}>
            {Object.entries(ACCOUNT_TYPE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
        <Field label="Açılış qalığı (₼)"><input type="number" step="0.01" value={f.openingBalance} required onChange={(e) => setF({ ...f, openingBalance: e.target.value })} /></Field>
        <Field label="Açılış tarixi"><DateInput value={f.openingDate} required onChange={(v) => setF({ ...f, openingDate: v })} /></Field>
        {REQ_FIELDS.map(([k, l]) => <Field key={k} label={l}><input value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} /></Field>)}
      </div>
    </FormModal>
  )
}

function MovementForm({ initial, accounts, accountId, onClose, onSaved }: { initial?: Movement; accounts: Account[]; accountId: number; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    date: initial?.date ?? today(), accountId: String(initial?.accountId ?? accountId), direction: (initial?.direction ?? 'IN') as Direction,
    amount: initial?.amount ?? '', purpose: initial?.purpose ?? 'CUSTOMER_PAYMENT', note: initial?.note ?? '', fileId: initial?.fileId ?? null,
  })
  const acc = accounts.find((a) => String(a.id) === f.accountId)
  return (
    <FormModal
      title={initial ? 'Hərəkəti dəyiş' : 'Yeni hərəkət'}
      onClose={onClose}
      onSubmit={async () => {
        const body = { ...f, accountId: Number(f.accountId) }
        if (initial) await put(`/movements/${initial.id}`, body)
        else await post('/movements', body)
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Hesab">
          <select value={f.accountId} onChange={(e) => setF({ ...f, accountId: e.target.value })}>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </Field>
        <Field label="Tarix"><DateInput value={f.date} required onChange={(v) => setF({ ...f, date: v })} /></Field>
        <Field label="İstiqamət">
          <select
            value={f.direction}
            onChange={(e) => {
              const direction = e.target.value as Direction
              // istiqamətə uyğun default təyinat: mədaxil → müştəri ödənişi, məxaric → xərc ödənişi
              const purpose = direction === 'OUT' && f.purpose === 'CUSTOMER_PAYMENT' ? 'EXPENSE' : direction === 'IN' && f.purpose === 'EXPENSE' ? 'CUSTOMER_PAYMENT' : f.purpose
              setF({ ...f, direction, purpose })
            }}
          >
            <option value="IN">Mədaxil (daxil oldu)</option>
            <option value="OUT">Məxaric (çıxdı)</option>
          </select>
        </Field>
        <Field label="Məbləğ (₼)"><input type="number" step="0.01" min="0" value={f.amount} required onChange={(e) => setF({ ...f, amount: e.target.value })} /></Field>
        <Field label="Təyinat" wide>
          <select value={f.purpose} onChange={(e) => setF({ ...f, purpose: e.target.value })}>
            {Object.entries(PURPOSE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
        <Field label="Qeyd" wide><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
        <FileField label="Sənəd (istəyə bağlı)" fileId={f.fileId} onChange={(id) => setF((x) => ({ ...x, fileId: id }))} />
      </div>
      {f.direction === 'IN' && (
        <p className="muted" style={{ margin: '0 0 8px' }}>
          {f.purpose === 'CUSTOMER_PAYMENT'
            ? 'Müştəri ödənişi yadda saxlanandan sonra "Satışa bağla" ilə satışa bağlanır.'
            : 'Yalnız "Müştəri ödənişi" təyinatlı mədaxil satışa bağlanır.'}
        </p>
      )}
      {acc?.type === 'DIRECTOR_CARD' && (
        <div className="explain">
          Direktorun şəxsi kartı: kartdan şirkət üçün ödəniş edəndə <b>Məxaric</b> + "Xərc ödənişi", direktora pul qaytaranda "Direktora qaytarış", kartı şirkət hesabından doldurmaq üçün "Hesablar arası köçürmə" seçin.
        </div>
      )}
    </FormModal>
  )
}

export function Accounts() {
  const accounts = useAsync(() => get<Account[]>('/accounts'), [])
  const [selId, setSelId] = useState<number>()
  const sel = accounts.data?.find((a) => a.id === selId) ?? defaultAccount(accounts.data)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const moves = useAsync(() => (sel ? get<Movement[]>('/movements' + qs({ accountId: sel.id, from, to })) : Promise.resolve([])), [sel?.id, from, to])
  const [editAcc, setEditAcc] = useState<Account | 'new'>()
  const [editMv, setEditMv] = useState<Movement | 'new'>()
  const [allocMv, setAllocMv] = useState<Movement>()
  // silmə / ayırma təsdiqi: pəncərədə nə silindiyi göstərilir, xəta da orada çıxır
  const [ask, setAsk] = useState<{ title: string; text: ReactNode; button: string; run: () => Promise<void> }>()
  const [err, setErr] = useState<unknown>()

  const refresh = () => {
    accounts.reload()
    moves.reload()
  }
  async function guarded(fn: () => Promise<void>) {
    try {
      await fn()
    } catch (e) {
      setErr(e)
    }
  }

  return (
    <>
      <PageHead title="Hesablar" sub="Bank hesabları, kassa və direktorun şəxsi kartı">
        <button className="btn" onClick={() => setEditAcc('new')}>+ Yeni hesab</button>
      </PageHead>
      <ErrorBox error={accounts.error ?? moves.error ?? err} />
      {accounts.loading && !accounts.data ? <Loading /> : (
        <div className="grid">
          {accounts.data?.map((a) => (
            <button
              key={a.id}
              className="card"
              style={{ textAlign: 'left', cursor: 'pointer', font: 'inherit', color: 'inherit', outline: a.id === sel?.id ? '2px solid var(--accent)' : undefined, marginBottom: 0 }}
              onClick={() => setSelId(a.id)}
            >
              <div className="k">{ACCOUNT_TYPE[a.type]} {a.isDefault && <Badge tone="ok">əsas</Badge>}</div>
              <div><b>{a.name}</b></div>
              <div className="v" style={{ color: num(a.balance) < 0 ? 'var(--bad)' : undefined }}>{money(a.balance)}</div>
            </button>
          ))}
        </div>
      )}
      {sel && (
        <div className="card">
          <div className="head">
            <h3>Rekvizitlər: {sel.name}</h3>
            <div className="actions">
              {sel.isDefault
                ? <span className="muted">Əsas hesab: formalarda avtomatik seçilir</span>
                : <button className="btn ghost sm" onClick={() => guarded(async () => { await post(`/accounts/${sel.id}/default`); refresh() })}>Əsas hesab et</button>}
              <button className="btn sm" onClick={() => setEditAcc(sel)}>✎ Hesabı və rekvizitləri dəyiş</button>
            </div>
          </div>
          {REQ_FIELDS.some(([k]) => sel[k]) ? (
            <table>
              <tbody>
                {REQ_FIELDS.filter(([k]) => sel[k]).map(([k, l]) => <tr key={k}><td className="muted" style={{ width: 160 }}>{l}</td><td><Copy text={sel[k]} /></td></tr>)}
              </tbody>
            </table>
          ) : (
            <div className="muted">Rekvizitlər hələ daxil edilməyib. "Hesabı və rekvizitləri dəyiş" düyməsi ilə əlavə edin.</div>
          )}
        </div>
      )}
      {sel && <StatementsBlock accountId={sel.id} onChanged={refresh} />}
      {sel && (
        <div className="card">
          <div className="head">
            <h3>{sel.name}: hərəkətlər</h3>
            <div className="actions">
              <button
                className="btn ghost sm"
                onClick={() => setAsk({
                  title: 'Hesab silinsin?', button: 'Hesabı sil',
                  text: <>"<b>{sel.name}</b>" hesabı silinəcək. Hərəkəti və ya xərci olan hesab silinmir.</>,
                  run: async () => { await del(`/accounts/${sel.id}`); setSelId(undefined); refresh() },
                })}
              >
                Hesabı sil
              </button>
              <button className="btn sm" onClick={() => setEditMv('new')}>+ Hərəkət</button>
            </div>
          </div>
          <div className="filters">
            <DateInput value={from} onChange={setFrom} title="Başlanğıc" />
            <DateInput value={to} onChange={setTo} title="Son" />
          </div>
          {!moves.data?.length ? <Empty>Hərəkət yoxdur.</Empty> : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Tarix</th><th>Təyinat</th><th>Qeyd</th><th>Satışa bağlantı</th><th className="n">Məbləğ</th><th /></tr></thead>
                <tbody>
                  {moves.data.map((m) => (
                    <tr key={m.id}>
                      <td>{fmtDate(m.date)}</td>
                      <td>{label(PURPOSE, m.purpose)} {m.expenseId && <Badge tone="info">avtomatik</Badge>}</td>
                      <td>{m.note} <FileLink id={m.fileId} text="sənəd" /> <StatementLink id={m.statementId} /></td>
                      <td>
                        {m.direction === 'IN' && !m.expenseId && (m.purpose === 'CUSTOMER_PAYMENT' || m.allocations.length > 0) && (
                          <>
                            <Badge tone={num(m.allocated) >= num(m.amount) ? 'ok' : num(m.allocated) > 0 ? 'warn' : 'muted'}>
                              {num(m.allocated) >= num(m.amount) ? 'bağlanıb' : num(m.allocated) > 0 ? 'qismən' : 'bağlanmayıb'}
                            </Badge>
                            {m.allocations.map((a) => (
                              <div key={a.id} className="muted">
                                <Link to={`/deals/${a.dealId}`}>{a.customerName ?? `Satış №${a.dealId}`}{a.contractNo ? ` · ${a.contractNo}` : ''}</Link> · {money(a.amount)}{' '}
                                <a href="#" onClick={(e) => { e.preventDefault(); setAsk({
                                  title: 'Bağlantı ayrılsın?', button: 'Ayır',
                                  text: <>{money(a.amount)} ödəniş "{a.customerName ?? `Satış №${a.dealId}`}" satışından ayrılacaq. Bank hərəkəti qalır.</>,
                                  run: async () => { await del(`/payment-allocations/${a.id}`); refresh() },
                                }) }}>Ayır</a>
                              </div>
                            ))}
                          </>
                        )}
                      </td>
                      <td className="n" style={{ color: m.direction === 'IN' ? 'var(--ok)' : 'var(--bad)' }}>
                        {m.direction === 'IN' ? '+' : '−'}{money(m.amount)}
                      </td>
                      <td className="n">
                        {!m.expenseId && (
                          <div className="actions" style={{ justifyContent: 'flex-end' }}>
                            {m.direction === 'IN' && m.purpose === 'CUSTOMER_PAYMENT' && num(m.amount) > num(m.allocated) && <button className="btn ghost sm" onClick={() => setAllocMv(m)}>Satışa bağla</button>}
                            <button className="btn ghost sm" onClick={() => setEditMv(m)}>Dəyiş</button>
                            <button className="btn ghost sm" onClick={() => setAsk({
                              title: 'Hərəkət silinsin?', button: 'Hərəkəti sil',
                              text: <>{fmtDate(m.date)} · {label(PURPOSE, m.purpose)} · <b>{m.direction === 'IN' ? '+' : '−'}{money(m.amount)}</b>{m.note ? ` · ${m.note}` : ''}. Bu əməliyyat geri qaytarılmır.</>,
                              run: async () => { await del(`/movements/${m.id}`); refresh() },
                            })}>Sil</button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="muted">Satış və xərclə yaranan hərəkətlər həmin səhifədən idarə olunur.</p>
        </div>
      )}
      {ask && <ConfirmModal title={ask.title} submitText={ask.button} onClose={() => setAsk(undefined)} onConfirm={async () => { await ask.run(); setAsk(undefined) }}>{ask.text}</ConfirmModal>}
      {allocMv && (
        <AllocateMovementModal
          movementId={allocMv.id}
          remaining={(num(allocMv.amount) - num(allocMv.allocated)).toFixed(2)}
          onClose={() => setAllocMv(undefined)}
          onSaved={() => { setAllocMv(undefined); refresh() }}
        />
      )}
      {editAcc && <AccountForm initial={editAcc === 'new' ? undefined : editAcc} onClose={() => setEditAcc(undefined)} onSaved={() => { setEditAcc(undefined); refresh() }} />}
      {editMv && sel && accounts.data && (
        <MovementForm
          initial={editMv === 'new' ? undefined : editMv}
          accounts={accounts.data}
          accountId={sel.id}
          onClose={() => setEditMv(undefined)}
          onSaved={() => { setEditMv(undefined); refresh() }}
        />
      )}
    </>
  )
}

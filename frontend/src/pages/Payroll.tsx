import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ActionButton, Badge, Copy, Empty, ErrorBox, Field, FileLink, FormModal, Loading, PageHead } from '../components/ui'
import { MonthInput } from '../components/MonthInput'
import { del, download, get, post, postFile, put, type Employee, type MovementSuggestion, type PayLineStatus, type PayLink, type PayrollLine, type Payments, type PayrollRun, type RunFile, type RunSummary } from '../lib/api'
import { cardDaysLeft, fmtDate, fmtMonth, money, monthName, num, thisMonth, WORKPLACE } from '../lib/format'
import { useAsync } from '../lib/useAsync'

const StatusBadge = ({ s }: { s: 'DRAFT' | 'FINAL' }) => <Badge tone={s === 'FINAL' ? 'ok' : 'warn'}>{s === 'FINAL' ? 'Yekunlaşıb' : 'Qaralama'}</Badge>

/** Kartın bitmə tarixi: bitib (qırmızı) və ya 60 gün ərzində bitir (narıncı); icon=true olanda yalnız xəbərdarlıq işarəsi */
function CardExpiry({ ym, icon }: { ym: string | null; icon?: boolean }) {
  const d = cardDaysLeft(ym)
  if (d === null) return null
  const text = d < 0 ? 'bitib' : `${d} gün qalıb`
  if (icon) return d <= 60 ? <span title={`Kartın bitmə tarixi ${fmtMonth(ym)}: ${text}`} style={{ color: d < 0 ? 'var(--bad)' : 'var(--warn-icon)' }}>⚠</span> : null
  return <div className="muted">Bitmə: {fmtMonth(ym)} {d < 0 ? <Badge tone="bad">bitib</Badge> : d <= 60 ? <Badge tone="warn">{text}</Badge> : null}</div>
}

function EmployeeForm({ initial, onClose, onSaved }: { initial?: Employee; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({
    name: initial?.name ?? '', position: initial?.position ?? '', workplace: initial?.workplace ?? 'MAIN',
    gross: initial?.gross ?? '', active: initial?.active ?? true,
    bankName: initial?.bankName ?? '', iban: initial?.iban ?? '', cardNumber: initial?.cardNumber ?? '', fin: initial?.fin ?? '', cardExpiry: initial?.cardExpiry ?? '', note: initial?.note ?? '',
  })
  return (
    <FormModal
      title={initial ? 'İşçini dəyiş' : 'Yeni işçi'}
      onClose={onClose}
      onSubmit={async () => {
        if (initial) await put(`/employees/${initial.id}`, { ...f, cardExpiry: f.cardExpiry || null })
        else await post('/employees', { ...f, cardExpiry: f.cardExpiry || null })
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Ad, soyad"><input value={f.name} required onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Vəzifə"><input value={f.position} onChange={(e) => setF({ ...f, position: e.target.value })} /></Field>
        <Field label="İş yeri">
          <select value={f.workplace} onChange={(e) => setF({ ...f, workplace: e.target.value as 'MAIN' | 'SECONDARY' })}>
            {Object.entries(WORKPLACE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </Field>
        <Field label="Müqavilə maaşı (gross, ₼)"><input type="number" step="0.01" min="0" value={f.gross} required onChange={(e) => setF({ ...f, gross: e.target.value })} /></Field>
        <Field label="Bank hesabı (hesab nömrəsi)"><input value={f.bankName} onChange={(e) => setF({ ...f, bankName: e.target.value })} /></Field>
        <Field label="IBAN"><input value={f.iban} onChange={(e) => setF({ ...f, iban: e.target.value })} /></Field>
        <Field label="Kart nömrəsi"><input value={f.cardNumber} onChange={(e) => setF({ ...f, cardNumber: e.target.value })} /></Field>
        <Field label="Kartın bitmə tarixi"><MonthInput value={f.cardExpiry} onChange={(v) => setF({ ...f, cardExpiry: v })} /></Field>
        <Field label="FİN"><input value={f.fin} onChange={(e) => setF({ ...f, fin: e.target.value })} /></Field>
        <Field label="Qeyd" wide><input value={f.note} onChange={(e) => setF({ ...f, note: e.target.value })} /></Field>
        <label className="check"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Aktiv</label>
      </div>
    </FormModal>
  )
}

export function Payroll() {
  const nav = useNavigate()
  const emps = useAsync(() => get<Employee[]>('/employees'), [])
  const runs = useAsync(() => get<RunSummary[]>('/payroll-runs'), [])
  const [edit, setEdit] = useState<Employee | 'new'>()
  const [month, setMonth] = useState(thisMonth())
  const [err, setErr] = useState<unknown>()

  return (
    <>
      <PageHead title="Əmək haqqı" sub="İşçilər və aylıq maaş cədvəlləri" />
      <ErrorBox error={emps.error ?? runs.error ?? err} />

      <div className="card">
        <div className="head">
          <h3>Aylıq cədvəllər</h3>
          <div className="actions">
            <MonthInput value={month} onChange={(v) => setMonth(v)} />
            <button
              className="btn"
              onClick={async () => {
                setErr(undefined)
                try {
                  const existing = runs.data?.find((r) => r.month === month)
                  if (existing) return nav(`/payroll/${existing.id}`)
                  const r = await post<PayrollRun>('/payroll-runs', { month })
                  nav(`/payroll/${r.id}`)
                } catch (e) {
                  setErr(e)
                }
              }}
            >
              Hesabla
            </button>
          </div>
        </div>
        {runs.loading && !runs.data ? <Loading /> : !runs.data?.length ? <Empty>Hələ maaş cədvəli yoxdur. Ay seçib "Hesabla" basın.</Empty> : (
          <table>
            <thead><tr><th>Ay</th><th>Status</th><th className="n">Gross</th><th className="n">Net (əlinə)</th><th className="n">Şirkətə xərc</th></tr></thead>
            <tbody>
              {[...runs.data].sort((a, b) => b.month.localeCompare(a.month)).map((r) => (
                <tr key={r.id} className="click" onClick={() => nav(`/payroll/${r.id}`)}>
                  <td><b>{monthName(r.month)}</b></td>
                  <td><StatusBadge s={r.status} /></td>
                  <td className="n">{money(r.totalGross)}</td>
                  <td className="n">{money(r.totalNet)}</td>
                  <td className="n">{money(r.totalEmployerCost)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <div className="head">
          <h3>İşçilər</h3>
          <button className="btn ghost sm" onClick={() => setEdit('new')}>+ Yeni işçi</button>
        </div>
        {!emps.data?.length ? <Empty>Hələ işçi yoxdur.</Empty> : (
          <table>
            <thead><tr><th>Ad</th><th>Vəzifə</th><th>İş yeri</th><th className="n">Gross</th><th>Bank / IBAN / kart</th><th /></tr></thead>
            <tbody>
              {emps.data.map((e) => (
                <tr key={e.id}>
                  <td><b>{e.name}</b> {!e.active && <Badge>passiv</Badge>}</td>
                  <td>{e.position}</td>
                  <td>{WORKPLACE[e.workplace]}</td>
                  <td className="n">{money(e.gross)}</td>
                  <td>
                    {e.bankName && <div className="muted">Bank hesabı: <Copy text={e.bankName} /></div>}
                    {e.iban && <div className="muted">IBAN: <Copy text={e.iban} /></div>}
                    {e.cardNumber && <div className="muted">Kart: <Copy text={e.cardNumber} /></div>}
                    <CardExpiry ym={e.cardExpiry} />
                    {e.fin && <div className="muted">FİN: {e.fin}</div>}
                  </td>
                  <td className="n">
                    <div className="actions" style={{ justifyContent: 'flex-end' }}>
                      <button className="btn ghost sm" onClick={() => setEdit(e)}>Dəyiş</button>
                      <button
                        className="btn ghost sm"
                        onClick={async () => {
                          if (!confirm(`"${e.name}" silinsin?`)) return
                          try {
                            await del(`/employees/${e.id}`)
                            emps.reload()
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
          </table>
        )}
      </div>
      {edit && <EmployeeForm initial={edit === 'new' ? undefined : edit} onClose={() => setEdit(undefined)} onSaved={() => { setEdit(undefined); emps.reload() }} />}
    </>
  )
}

function DaysInput({ line, disabled, onSave }: { line: PayrollLine; disabled: boolean; onSave: (v: number) => void }) {
  const [v, setV] = useState(String(line.workedDays))
  const commit = () => {
    const n = Number(v)
    if (!Number.isNaN(n) && n !== line.workedDays) onSave(n)
  }
  return (
    <input
      type="number" min="0" step="1" value={v} disabled={disabled}
      onChange={(e) => setV(e.target.value)} onBlur={commit}
      onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()}
    />
  )
}

const LINE_STATUS: Record<PayLineStatus, { text: string; tone: 'ok' | 'info' | 'warn' }> = {
  PAID: { text: 'ödənilib', tone: 'ok' },
  PARTIAL: { text: 'qismən', tone: 'info' },
  UNPAID: { text: 'gözlənilir', tone: 'warn' },
}
export const LineStatusBadge = ({ s }: { s: PayLineStatus }) => <Badge tone={LINE_STATUS[s].tone}>{LINE_STATUS[s].text}</Badge>

/** Sətri (vergi kodu və ya işçinin NET-i) bank əməliyyatına bağlama pəncərəsi: təkliflər sıralanıb, ən yaxşısı əvvəlcədən seçilir */
function LinkMovementModal({ runId, codeKey, employeeId, title, remaining, onClose, onSaved }: {
  runId: number; codeKey: string; employeeId?: number | null; title: string; remaining: number; onClose: () => void; onSaved: () => void
}) {
  const list = useAsync(() => get<MovementSuggestion[]>(`/payroll-runs/${runId}/movement-suggestions?codeKey=${codeKey}${employeeId ? `&employeeId=${employeeId}` : ''}`), [runId, codeKey, employeeId])
  const [movId, setMovId] = useState('')
  const [amount, setAmount] = useState('')
  const mv = list.data?.find((m) => String(m.movementId) === movId)
  function pick(id: string, from = list.data) {
    setMovId(id)
    const m = from?.find((x) => String(x.movementId) === id)
    if (m) setAmount(Math.min(num(m.remaining), remaining).toFixed(2))
  }
  useEffect(() => {
    const best = list.data?.[0]
    if (best && best.score > 0 && !movId) pick(String(best.movementId), list.data)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list.data])
  return (
    <FormModal
      title={`Bank əməliyyatını bağla: ${title}`}
      submitText="Bağla"
      onClose={onClose}
      onSubmit={async () => {
        await post(`/payroll-runs/${runId}/payments/link`, { codeKey, employeeId: employeeId ?? undefined, movementId: Number(movId), amount: amount || undefined })
        onSaved()
      }}
    >
      {list.loading && !list.data ? <Loading /> : !list.data?.length ? (
        <Empty>Bağlanmamış bank çıxışı (təyinat: maaş və ya vergi) yoxdur. Əvvəl Hesablar bölməsində hərəkəti yazın.</Empty>
      ) : (
        <div className="form">
          <Field label={`Bank çıxışı (uyğunluq balına görə sıralanıb). Sətrin qalığı: ${money(remaining)}`} wide>
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
          <Field label={`Bağlanan məbləğ (₼)${mv ? `, hərəkət qalığı ${money(mv.remaining)}` : ''}`}>
            <input type="number" step="0.01" min="0.01" value={amount} required onChange={(e) => setAmount(e.target.value)} />
          </Field>
        </div>
      )}
    </FormModal>
  )
}

/** Bağlı bank əməliyyatlarının tarixi və "Ayır" düyməsi */
function Links({ links, onUnlink }: { links: PayLink[]; onUnlink: (id: number) => void }) {
  if (!links.length) return <span className="muted">—</span>
  return (
    <>
      {links.map((l) => (
        <div key={l.id} style={{ whiteSpace: 'nowrap' }}>
          {fmtDate(l.date)} · {money(l.amount)}{' '}
          <button className="btn ghost sm" onClick={() => onUnlink(l.id)}>Ayır</button>
        </div>
      ))}
    </>
  )
}

function RunFiles({ runId }: { runId: number }) {
  const files = useAsync(() => get<RunFile[]>(`/payroll-runs/${runId}/files`), [runId])
  const [title, setTitle] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  return (
    <div className="card">
      <h3>Sənədlər</h3>
      <p className="muted" style={{ marginTop: 0 }}>Bankın maaş ödəniş qəbzi, ödəniş tapşırıqları və s.</p>
      <ErrorBox error={err ?? files.error} />
      {!files.data?.length ? <Empty>Hələ sənəd yoxdur.</Empty> : (
        <table>
          <thead><tr><th>Ad</th><th>Fayl</th><th>Yüklənib</th><th /></tr></thead>
          <tbody>
            {files.data.map((f) => (
              <tr key={f.id}>
                <td><b>{f.title}</b></td>
                <td><FileLink id={f.fileId} text={f.name} /></td>
                <td>{fmtDate(f.createdAt)}</td>
                <td className="n">
                  <button
                    className="btn ghost sm"
                    onClick={async () => {
                      if (!confirm(`"${f.title}" silinsin?`)) return
                      setErr(undefined)
                      try {
                        await del(`/payroll-runs/${runId}/files/${f.id}`)
                        files.reload()
                      } catch (e) {
                        setErr(e)
                      }
                    }}
                  >
                    Sil
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="actions" style={{ marginTop: 12 }}>
        <input placeholder="Sənədin adı (məs. Maaş ödəniş qəbzi)" value={title} onChange={(e) => setTitle(e.target.value)} style={{ minWidth: 260 }} />
        <label className={`btn ghost sm${busy ? ' disabled' : ''}`}>
          {busy ? 'Yüklənir...' : 'Sənəd yüklə'}
          <input
            type="file" hidden disabled={busy}
            onChange={async (e) => {
              const file = e.target.files?.[0]
              e.target.value = ''
              if (!file) return
              setBusy(true)
              setErr(undefined)
              try {
                await postFile<RunFile>(`/payroll-runs/${runId}/files`, file, { title: title.trim() })
                setTitle('')
                files.reload()
              } catch (x) {
                setErr(x)
              } finally {
                setBusy(false)
              }
            }}
          />
        </label>
      </div>
    </div>
  )
}

function BankPayments({ runId, refresh }: { runId: number; refresh: string }) {
  const pay = useAsync(() => get<Payments>(`/payroll-runs/${runId}/payments`), [runId, refresh])
  const [linking, setLinking] = useState<{ codeKey: string; employeeId?: number | null; title: string; remaining: number }>()
  const [err, setErr] = useState<unknown>()
  const rows = pay.data?.taxes ?? []
  const emps = pay.data?.employees ?? []
  const total = rows.reduce((s, p) => s + num(p.amount), 0)
  const plain = (v: string) => num(v).toFixed(2)
  const unlink = async (id: number) => {
    if (!confirm('Bank əməliyyatı ilə bağlantı ayrılsın? (Bank hərəkətinin özü silinmir.)')) return
    setErr(undefined)
    try {
      await del(`/payroll-payment-links/${id}`)
      pay.reload()
    } catch (e) {
      setErr(e)
    }
  }
  const linkBtn = (status: PayLineStatus, codeKey: string, title: string, amount: string, paid: string, employeeId?: number | null) =>
    status !== 'PAID' && (
      <button className="btn ghost sm" onClick={() => setLinking({ codeKey, employeeId, title, remaining: Math.max(0, num(amount) - num(paid)) })}>Bank əməliyyatını bağla</button>
    )
  return (
    <div className="card">
      <h3>Bank ödənişləri</h3>
      <p className="muted" style={{ marginTop: 0 }}>ABB Biznes-ə bir-bir köçürmək üçün: hər dəyərin yanındakı 📋 düyməsi yalnız həmin dəyəri kopyalayır. Ödəniş edildikdən sonra sətri bank əməliyyatına bağlayın.</p>
      <ErrorBox error={pay.error ?? err} />
      {!rows.length ? <Empty>Ödəniş yoxdur (məbləğ 0 və ya bütün kodlar passivdir).</Empty> : (
        <div className="table-wrap">
          <table className="pay">
            <thead><tr><th>№</th><th>Təyinat</th><th>Büdcə kodu</th><th className="n">Məbləğ</th><th>Ödəniş təyinatı mətni</th><th>Status</th><th>Bank əməliyyatı</th><th /></tr></thead>
            <tbody>
              {rows.map((p) => (
                <tr key={p.key} className={p.status === 'PAID' ? 'paid' : ''}>
                  <td>{p.order}</td>
                  <td>{p.title}</td>
                  <td><Copy text={p.budgetCode} /></td>
                  <td className="n"><Copy text={plain(p.amount)}>{money(p.amount)}</Copy></td>
                  <td><Copy text={p.purpose} /></td>
                  <td><LineStatusBadge s={p.status} />{p.status === 'PARTIAL' && <div className="muted">{money(p.paidAmount)}</div>}</td>
                  <td><Links links={p.links} onUnlink={unlink} /></td>
                  <td>{linkBtn(p.status, p.key, p.title, p.amount, p.paidAmount)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot><tr><th colSpan={3}>Cəmi</th><th className="n">{money(total)}</th><th colSpan={4} /></tr></tfoot>
          </table>
        </div>
      )}
      {emps.length > 0 && (
        <>
          <h3 style={{ marginTop: 18 }}>İşçilərin maaş ödənişləri</h3>
          <div className="table-wrap">
            <table className="pay">
              <thead><tr><th>№</th><th>İşçi</th><th>Bank hesabı</th><th>IBAN</th><th>Kart</th><th className="n">Məbləğ</th><th>Təyinat</th><th>Status</th><th>Bank əməliyyatı</th><th /></tr></thead>
              <tbody>
                {emps.map((e, i) => (
                  <tr key={e.employeeId ?? e.name} className={e.status === 'PAID' ? 'paid' : ''}>
                    <td>{i + 1}</td>
                    <td><b>{e.name}</b></td>
                    <td><Copy text={e.bankName} /></td>
                    <td><Copy text={e.iban} /></td>
                    <td><Copy text={e.cardNumber} /> <CardExpiry ym={e.cardExpiry} icon /></td>
                    <td className="n"><Copy text={plain(e.amount)}>{money(e.amount)}</Copy></td>
                    <td><Copy text={e.purpose} /></td>
                    <td><LineStatusBadge s={e.status} /></td>
                    <td><Links links={e.links} onUnlink={unlink} /></td>
                    <td>{e.employeeId && linkBtn(e.status, 'NET', `${e.name}, əmək haqqı`, e.amount, e.paidAmount, e.employeeId)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr><th colSpan={5}>Cəmi</th><th className="n">{money(emps.reduce((s, e) => s + num(e.amount), 0))}</th><th colSpan={4} /></tr></tfoot>
            </table>
          </div>
        </>
      )}
      {linking && (
        <LinkMovementModal runId={runId} {...linking} onClose={() => setLinking(undefined)} onSaved={() => { setLinking(undefined); pay.reload() }} />
      )}
    </div>
  )
}

export function PayrollRunView() {
  const { id } = useParams()
  const nav = useNavigate()
  const run = useAsync(() => get<PayrollRun>(`/payroll-runs/${id}`), [id])
  const [err, setErr] = useState<unknown>()
  const r = run.data
  if (run.loading && !r) return <Loading />
  if (!r) return <ErrorBox error={run.error} />
  const locked = r.status === 'FINAL'
  const t = r.totals

  async function act(fn: () => Promise<unknown>) {
    setErr(undefined)
    try {
      await fn()
      run.reload()
    } catch (e) {
      setErr(e)
    }
  }

  return (
    <>
      <PageHead title={`Maaş cədvəli: ${monthName(r.month)}`} sub={`Norma: ${r.normDays} iş günü`}>
        <StatusBadge s={r.status} />
        <ActionButton onRun={() => download(`/payroll-runs/${r.id}.xlsx`, `maas-${r.month}.xlsx`)}>Excel</ActionButton>
        {locked ? (
          <button className="btn ghost" onClick={() => act(() => post(`/payroll-runs/${r.id}/reopen`))}>Yenidən aç</button>
        ) : (
          <>
            <button className="btn" onClick={() => act(() => post(`/payroll-runs/${r.id}/finalize`))}>Yekunlaşdır</button>
            <button
              className="btn danger"
              onClick={async () => {
                if (!confirm('Qaralama cədvəl silinsin?')) return
                try {
                  await del(`/payroll-runs/${r.id}`)
                  nav('/payroll')
                } catch (e) {
                  setErr(e)
                }
              }}
            >
              Sil
            </button>
          </>
        )}
      </PageHead>
      <p className="muted"><Link to="/payroll">← Əmək haqqı</Link>{locked ? ' · Yekunlaşmış cədvəl dəyişmir. Dəyişmək üçün "Yenidən aç" basın.' : ' · İşlənmiş günü dəyişin, məbləğlər avtomatik yenilənir.'}</p>
      <ErrorBox error={err ?? run.error} />

      <div className="card table-wrap">
        <table className="sheet">
          <thead>
            <tr>
              <th>İşçi</th><th>İş yeri</th><th className="n">Gross</th><th className="n">Norma</th><th className="n">İşlədi</th>
              <th className="n">Hesablanıb</th><th className="n">Gəlir vergisi</th><th className="n">DSMF (işçi)</th><th className="n">İTS (işçi)</th>
              <th className="n">İşsizlik (işçi)</th><th className="n">NET</th><th className="n">DSMF (şirkət)</th><th className="n">İTS (şirkət)</th>
              <th className="n">İşsizlik (şirkət)</th><th className="n">Şirkətə xərc</th>
            </tr>
          </thead>
          <tbody>
            {r.lines.map((l) => (
              <tr key={l.id}>
                <td><b>{l.name}</b><div className="muted">{l.position}</div></td>
                <td>{l.workplace === 'MAIN' ? 'Əsas' : 'Əlavə'}</td>
                <td className="n">{money(l.gross)}</td>
                <td className="n">{l.normDays}</td>
                <td className="n">
                  <DaysInput key={`${l.id}-${l.workedDays}`} line={l} disabled={locked} onSave={(v) => act(() => put(`/payroll-runs/${r.id}/lines/${l.id}`, { workedDays: v }))} />
                </td>
                <td className="n">{money(l.accrued)}</td>
                <td className="n">{money(l.income)}</td>
                <td className="n">{money(l.dsmfEmp)}</td>
                <td className="n">{money(l.medEmp)}</td>
                <td className="n">{money(l.unempEmp)}</td>
                <td className="n"><b>{money(l.net)}</b></td>
                <td className="n">{money(l.dsmfEr)}</td>
                <td className="n">{money(l.medEr)}</td>
                <td className="n">{money(l.unempEr)}</td>
                <td className="n">{money(l.employerCost)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th colSpan={2}>Cəmi</th><th className="n">{money(t.gross)}</th><th /><th />
              <th className="n">{money(t.accrued)}</th><th className="n">{money(t.income)}</th><th className="n">{money(t.dsmfEmp)}</th>
              <th className="n">{money(t.medEmp)}</th><th className="n">{money(t.unempEmp)}</th><th className="n">{money(t.net)}</th>
              <th className="n">{money(t.dsmfEr)}</th><th className="n">{money(t.medEr)}</th><th className="n">{money(t.unempEr)}</th>
              <th className="n">{money(t.employerCost)}</th>
            </tr>
          </tfoot>
        </table>
      </div>

      <BankPayments runId={r.id} refresh={`${t.net}|${t.income}|${t.employerCost}`} />
      <RunFiles runId={r.id} />
    </>
  )
}

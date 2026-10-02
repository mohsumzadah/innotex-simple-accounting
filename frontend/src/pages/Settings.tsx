import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { DocxPreview } from '../components/DocxPreview'
import { MonthInput } from '../components/MonthInput'
import { Badge, ConfirmModal, Copy, Empty, ErrorBox, Field, FormModal, Loading, Modal, PageHead } from '../components/ui'
import { del, download, get, post, put, qs, uploadFile, ROLE_LABEL, type AppUser, type CalendarMonth, type PaymentCode, type Role, type PayrollRate, type Settings as S, type Template } from '../lib/api'
import { fmtDateTime, fmtMonth, monthName, thisMonth } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { useSession } from '../lib/session'

type Tab = 'company' | 'codes' | 'rates' | 'calendar' | 'templates' | 'users' | 'password' | 'backup'
const TABS: [Tab, string][] = [
  ['company', 'Şirkət'], ['rates', 'Maaş dərəcələri'], ['codes', 'Ödəniş kodları'], ['calendar', 'İş günü cədvəli'], ['templates', 'Şablonlar'], ['backup', 'Ehtiyat nüsxə'], ['users', 'İstifadəçilər'], ['password', 'Parol'],
]

export function Settings() {
  const { isAdmin } = useSession()
  const [tab, setTab] = useState<Tab>('company')
  return (
    <>
      <PageHead title="Ayarlar" />
      <p className="muted" style={{ marginTop: 0 }}>Xərc maddələri və Nomenklatura artıq ayrıca səhifələrdədir: <Link to="/expense-items">Xərc maddələri</Link>, <Link to="/products">Nomenklatura</Link>.</p>
      <div className="tabs">
        {TABS.filter(([k]) => k !== 'users' || isAdmin).map(([k, l]) => <button key={k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{l}</button>)}
      </div>
      {tab === 'company' && <Company />}
      {tab === 'rates' && <Rates />}
      {tab === 'codes' && <PaymentCodes />}
      {tab === 'calendar' && <Calendar />}
      {tab === 'templates' && <Templates />}
      {tab === 'backup' && <Backup />}
      {tab === 'users' && isAdmin && <Users />}
      {tab === 'password' && <Password />}
    </>
  )
}

/** Kiçik "Yadda saxla" yönümlü yardımçı: nəticəni yaşıl bildirişlə göstərir */
function useSave() {
  const [msg, setMsg] = useState('')
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>, ok = 'Yadda saxlanıldı') => {
    setBusy(true)
    setErr(undefined)
    setMsg('')
    try {
      await fn()
      setMsg(ok)
    } catch (e) {
      setErr(e)
    } finally {
      setBusy(false)
    }
  }
  return { msg, err, busy, run }
}

const COMPANY_FIELDS: [keyof S, string][] = [
  ['companyName', 'Şirkətin adı'], ['voen', 'VÖEN'], ['director', 'Direktor'], ['address', 'Ünvan'], ['bank', 'Bank'],
  ['iban', 'IBAN'], ['bankCode', 'Bank kodu'], ['bankVoen', 'Bankın VÖEN-i'], ['swift', 'SWIFT'], ['correspondentAccount', 'Müxbir hesab'], ['phone', 'Telefon'], ['email', 'E-poçt'],
]

function Company() {
  const s = useAsync(() => get<S>('/settings'), [])
  const [f, setF] = useState<S>()
  const [seen, setSeen] = useState<S>()
  const save = useSave()
  if (s.data && s.data !== seen) {
    setSeen(s.data)
    setF(s.data)
  }
  if (!f) return s.error ? <ErrorBox error={s.error} /> : <Loading />
  return (
    <>
      <div className="card">
        <h3>Şirkət rekvizitləri</h3>
        <p className="muted" style={{ marginTop: 0 }}>Müqavilə, protokol və aktlarda avtomatik istifadə olunur.</p>
        <div className="form">
          {COMPANY_FIELDS.map(([k, l]) => (
            <Field key={k} label={l}><input value={f[k] ?? ''} onChange={(e) => setF({ ...f, [k]: e.target.value })} /></Field>
          ))}
        </div>
      </div>
      <div className="card">
        <h3>Mənfəət vergisi</h3>
        <div className="form" style={{ maxWidth: 260 }}>
          <Field label="Dərəcə (%)"><input type="number" step="0.01" min="0" value={f.profitTaxRate ?? ''} onChange={(e) => setF({ ...f, profitTaxRate: e.target.value })} /></Field>
        </div>
      </div>
      <div className="card">
        <h3>Vergi uçotu metodu</h3>
        <p><b>Hesablama metodu</b> (yalnız bu metod dəstəklənir)</p>
        <p className="muted">Gəlir qaimə tarixində, xərc sənəd (invoys) tarixində, maaş aid olduğu ayda tanınır. Ödəniş tarixi yalnız hesab hərəkətini müəyyən edir.</p>
      </div>
      <ErrorBox error={save.err} />
      {save.msg && <div className="notice">{save.msg}</div>}
      <p className="muted">Hesabları <Link to="/accounts">Hesablar</Link> səhifəsində idarə edə bilərsiniz.</p>
      <button className="btn" disabled={save.busy} onClick={() => save.run(() => put('/settings', f))}>Yadda saxla</button>
    </>
  )
}

const RATE_FIELDS:[keyof PayrollRate, string][] = [
  ['dsmfLimit', 'DSMF hədd (₼)'], ['dsmfEmpLow', 'DSMF işçi, hədd daxili %'], ['dsmfEmpHigh', 'DSMF işçi, hədd üstü %'],
  ['dsmfErLow', 'DSMF şirkət, hədd daxili %'], ['dsmfErHigh', 'DSMF şirkət, hədd üstü %'],
  ['unempEmp', 'İşsizlik işçi %'], ['unempEr', 'İşsizlik şirkət %'],
  ['medLimit', 'İTS hədd (₼)'], ['medLow', 'İTS hədd daxili %'], ['medHigh', 'İTS hədd üstü %'],
  ['incomeLimit', 'Gəlir vergisi hədd (₼)'], ['incomeExempt', 'Gəlir vergisindən azad (₼, əsas iş yeri)'],
  ['incomeLow', 'Gəlir vergisi hədd daxili %'], ['incomeHigh', 'Gəlir vergisi hədd üstü %'],
]

function Rates() {
  const rates = useAsync(() => get<PayrollRate[]>('/payroll-rates'), [])
  const [adding, setAdding] = useState(false)
  const sorted = [...(rates.data ?? [])].sort((a, b) => b.validFrom.localeCompare(a.validFrom))
  const last = sorted[0]
  const [f, setF] = useState<Record<string, string>>({})

  function open() {
    const init: Record<string, string> = { validFrom: thisMonth() }
    RATE_FIELDS.forEach(([k]) => (init[k] = last ? String(last[k]) : ''))
    setF(init)
    setAdding(true)
  }

  return (
    <div className="card table-wrap">
      <div className="head">
        <h3>Maaş dərəcələri (tarixçə)</h3>
        <button className="btn sm" onClick={open}>+ Yeni sətir</button>
      </div>
      <p className="muted" style={{ marginTop: 0 }}>Yeni qanun çıxanda yeni sətir əlavə edin. Köhnə sətirlər dəyişmir, hər ay öz tarixinə uyğun dərəcə ilə hesablanır.</p>
      <ErrorBox error={rates.error} />
      {!sorted.length ? <Empty>Dərəcə yoxdur.</Empty> : (
        <table>
          <thead>
            <tr><th>Qüvvədədir</th>{RATE_FIELDS.map(([k, l]) => <th key={k} className="n" style={{ whiteSpace: 'normal' }}>{l}</th>)}</tr>
          </thead>
          <tbody>
            {sorted.map((r) => (
              <tr key={r.id}>
                <td><b>{fmtMonth(r.validFrom)}</b></td>
                {RATE_FIELDS.map(([k]) => <td key={k} className="n">{r[k]}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {adding && (
        <FormModal
          title="Yeni dərəcə sətri"
          onClose={() => setAdding(false)}
          onSubmit={async () => {
            await post('/payroll-rates', f)
            setAdding(false)
            rates.reload()
          }}
        >
          <div className="form">
            <Field label="Qüvvəyə minir (ay)"><MonthInput value={f.validFrom ?? ''} required onChange={(v) => setF({ ...f, validFrom: v })} /></Field>
            {RATE_FIELDS.map(([k, l]) => (
              <Field key={k} label={l}><input type="number" step="0.01" min="0" value={f[k] ?? ''} required onChange={(e) => setF({ ...f, [k]: e.target.value })} /></Field>
            ))}
          </div>
        </FormModal>
      )}
    </div>
  )
}

function Calendar() {
  const [year, setYear] = useState(() => new Date().getFullYear())
  const cal = useAsync(() => get<CalendarMonth[]>(`/work-calendar${qs({ year })}`), [year])
  const [days, setDays] = useState<Record<string, string>>({})
  const [seen, setSeen] = useState<CalendarMonth[]>()
  const save = useSave()
  if (cal.data && cal.data !== seen) {
    setSeen(cal.data)
    setDays(Object.fromEntries(cal.data.map((m) => [m.month, String(m.days)])))
  }
  return (
    <div className="card" style={{ maxWidth: 520 }}>
      <div className="head">
        <h3>İş günü cədvəli</h3>
        <input type="number" value={year} style={{ width: 90 }} onChange={(e) => setYear(Number(e.target.value) || year)} />
      </div>
      <p className="muted" style={{ marginTop: 0 }}>Hər ayın norma iş günü sayı (maaşın işlənmiş günə görə hesablanması üçün).</p>
      <ErrorBox error={cal.error ?? save.err} />
      {cal.loading && !cal.data ? <Loading /> : (
        <table>
          <tbody>
            {cal.data?.map((m) => (
              <tr key={m.month}>
                <td>{monthName(m.month)}</td>
                <td style={{ width: 100 }}><input type="number" min="0" max="31" value={days[m.month] ?? ''} onChange={(e) => setDays({ ...days, [m.month]: e.target.value })} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {save.msg && <div className="notice" style={{ marginTop: 10 }}>{save.msg}</div>}
      <div style={{ marginTop: 12 }}>
        <button className="btn" disabled={save.busy} onClick={() => save.run(() => put(`/work-calendar${qs({ year })}`, Object.entries(days).map(([month, d]) => ({ month, days: Number(d) }))).then(() => cal.reload()))}>
          Yadda saxla
        </button>
      </div>
    </div>
  )
}

const PLACEHOLDERS = [
  ...['name', 'voen', 'address', 'director', 'bank', 'iban', 'bankCode', 'bankVoen', 'swift', 'correspondentAccount', 'phone', 'email'].map((k) => `company.${k}`),
  ...['name', 'voen', 'address', 'director', 'bank', 'iban', 'bankCode', 'bankVoen', 'swift', 'correspondentAccount', 'phone', 'email'].map((k) => `customer.${k}`),
  ...['contractNo', 'contractDate', 'price', 'priceWords', 'quantity', 'unitPrice', 'computers', 'product', 'actNo', 'actDate', 'protocolDate'].map((k) => `deal.${k}`),
  ...['name', 'code', 'unit', 'description'].map((k) => `product.${k}`),
  'today',
]

function Templates() {
  const list = useAsync(() => get<Template[]>('/templates'), [])
  const [edit, setEdit] = useState<Template | { code: string }>()
  const [preview, setPreview] = useState<Template>()
  const [err, setErr] = useState<unknown>()
  const pick = useRef<HTMLInputElement>(null)
  // fayl seçiləndə: 'new:<code>' = yeni Word şablonu, 'replace:<id>' = mövcud şablonun faylını dəyiş
  const target = useRef('')
  const choose = (t: string) => {
    target.current = t
    pick.current?.click()
  }
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
    <>
      <ErrorBox error={list.error ?? err} />
      {list.loading && !list.data && <Loading />}
      <input
        ref={pick}
        type="file"
        accept=".docx"
        hidden
        onChange={(e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (!f) return
          const [kind, key] = target.current.split(':')
          guard(async () => {
            const ref = await uploadFile(f)
            if (kind === 'new') {
              await post('/templates', { code: key, title: f.name.replace(/\.docx$/i, ''), format: 'DOCX', fileId: ref.id, isDefault: false })
            } else {
              const t = (list.data ?? []).find((x) => String(x.id) === key)
              if (t) await put(`/templates/${t.id}`, { ...t, format: 'DOCX', fileId: ref.id })
            }
          })
        }}
      />
      {DOC_CODES.map(([code, name]) => {
        const items = (list.data ?? []).filter((t) => t.code === code)
        return (
          <div className="card" key={code}>
            <div className="head">
              <h3>{name} şablonları</h3>
              <div className="actions">
                <button className="btn ghost sm" onClick={() => choose(`new:${code}`)}>Word şablonu yüklə</button>
                <button className="btn sm" onClick={() => setEdit({ code })}>+ Yeni şablon</button>
              </div>
            </div>
            {!items.length ? <Empty>Şablon yoxdur.</Empty> : (
              <table>
                <tbody>
                  {items.map((t) => (
                    <tr key={t.id}>
                      <td><b>{t.title}</b> {t.format === 'DOCX' && <Badge>Word</Badge>} {t.isDefault && <Badge tone="ok">əsas</Badge>}</td>
                      <td className="n">
                        <div className="actions" style={{ justifyContent: 'flex-end' }}>
                          {!t.isDefault && <button className="btn ghost sm" onClick={() => guard(() => put(`/templates/${t.id}`, { ...t, isDefault: true }))}>Əsas et</button>}
                          {t.format === 'DOCX' ? (
                            <>
                              <button className="btn ghost sm" onClick={() => setPreview(t)}>Önizləmə</button>
                              <button className="btn ghost sm" onClick={() => choose(`replace:${t.id}`)}>Faylı dəyiş</button>
                              <button className="btn ghost sm" onClick={() => guard(() => download(`/files/${t.fileId}`, `${t.title}.docx`))}>Endir</button>
                            </>
                          ) : (
                            <button className="btn ghost sm" onClick={() => setEdit(t)}>Redaktə</button>
                          )}
                          <button
                            className="btn ghost sm"
                            onClick={() => {
                              const title = prompt('Yeni ad', t.title)
                              if (title && title !== t.title) guard(() => put(`/templates/${t.id}`, { ...t, title }))
                            }}
                          >
                            Adını dəyiş
                          </button>
                          <button className="btn ghost sm" onClick={() => confirm(`"${t.title}" silinsin?`) && guard(() => del(`/templates/${t.id}`))}>Sil</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )
      })}
      {edit && <TemplateEditor tpl={edit} onClose={() => setEdit(undefined)} onSaved={() => { setEdit(undefined); list.reload() }} />}
      {preview && preview.fileId && (
        <Modal xl title={`${preview.title}: Word şablonu`} onClose={() => setPreview(undefined)}>
          <p className="muted" style={{ marginTop: 0 }}>Word-da müvafiq yerə yer tutucunu yazın, məs. {'{{customer.name}}'}. Kopyalayıb Word faylına yapışdıra bilərsiniz:</p>
          <div className="chips" style={{ marginBottom: 12 }}>
            {PLACEHOLDERS.map((p) => <span key={p}><Copy text={`{{${p}}}`}><code>{`{{${p}}}`}</code></Copy></span>)}
          </div>
          <DocxPreview path={`/files/${preview.fileId}`} />
          <div className="actions"><button className="btn ghost" onClick={() => setPreview(undefined)}>Bağla</button></div>
        </Modal>
      )}
    </>
  )
}

function TemplateEditor({ tpl, onClose, onSaved }: { tpl: Template | { code: string }; onClose: () => void; onSaved: () => void }) {
  const existing = 'id' in tpl ? tpl : undefined
  const [title, setTitle] = useState(existing?.title ?? '')
  const [html, setHtml] = useState(existing?.html ?? '')
  const area = useRef<HTMLTextAreaElement>(null)

  function insert(p: string) {
    const el = area.current
    const ins = `{{${p}}}`
    if (!el) return setHtml(html + ins)
    const a = el.selectionStart
    const b = el.selectionEnd
    setHtml(html.slice(0, a) + ins + html.slice(b))
    requestAnimationFrame(() => {
      el.focus()
      el.setSelectionRange(a + ins.length, a + ins.length)
    })
  }

  return (
    <FormModal
      title={existing ? 'Şablonu redaktə et' : 'Yeni şablon'}
      onClose={onClose}
      onSubmit={async () => {
        if (existing) await put(`/templates/${existing.id}`, { ...existing, title, html })
        else await post('/templates', { code: tpl.code, title, html, isDefault: false })
        onSaved()
      }}
    >
      <div className="form" style={{ marginBottom: 10 }}>
        <Field label="Şablonun adı" wide><input value={title} required onChange={(e) => setTitle(e.target.value)} /></Field>
      </div>
      <p className="muted" style={{ marginTop: 0 }}>Yerdəyişmələrə klikləsəniz, kursorun yerinə əlavə olunur:</p>
      <div className="chips" style={{ marginBottom: 12 }}>
        {PLACEHOLDERS.map((p) => <code key={p} onClick={() => insert(p)}>{`{{${p}}}`}</code>)}
      </div>
      <textarea ref={area} className="code" value={html} onChange={(e) => setHtml(e.target.value)} spellCheck={false} />
    </FormModal>
  )
}

const DOC_CODES: [string, string][] = [['CONTRACT', 'Müqavilə'], ['PROTOCOL', 'Protokol'], ['ACT', 'Akt']]

type UserForm = { id?: number; email: string; name: string; role: Role; active: boolean; password: string }

/** İstifadəçilər (yalnız administrator): yaratmaq, rol, passiv etmək, parolu sıfırlamaq, silmək */
function Users() {
  const { me } = useSession()
  const users = useAsync(() => get<AppUser[]>('/users'), [])
  const [f, setF] = useState<UserForm>()
  const [removing, setRemoving] = useState<AppUser>()
  const editing = f?.id !== undefined
  const self = editing && f?.id === me?.id

  return (
    <div className="card table-wrap">
      <div className="head">
        <h3>İstifadəçilər</h3>
        <button className="btn sm" onClick={() => setF({ email: '', name: '', role: 'ACCOUNTANT', active: true, password: '' })}>+ Yeni istifadəçi</button>
      </div>
      <p className="muted" style={{ marginTop: 0 }}>
        <b>Mühasib</b> bütün uçot bölmələri ilə işləyir, amma istifadəçiləri idarə edə bilmir. <b>Administrator</b> bundan əlavə istifadəçiləri yaradır və dəyişir.
        Passiv istifadəçi daxil ola bilmir.
      </p>
      <ErrorBox error={users.error} />
      {!users.data ? <Loading /> : !users.data.length ? <Empty>İstifadəçi yoxdur.</Empty> : (
        <table>
          <thead><tr><th>Ad</th><th>E-poçt</th><th>Rol</th><th>Status</th><th>Yaradılıb</th><th /></tr></thead>
          <tbody>
            {users.data.map((u) => (
              <tr key={u.id}>
                <td>{u.name || '—'}{u.id === me?.id && <span className="muted"> (siz)</span>}</td>
                <td>{u.email}</td>
                <td><Badge tone={u.role === 'ADMIN' ? 'info' : 'muted'}>{ROLE_LABEL[u.role]}</Badge></td>
                <td>{u.active ? <Badge tone="ok">aktiv</Badge> : <Badge tone="bad">passiv</Badge>}</td>
                <td>{fmtDateTime(u.createdAt)}</td>
                <td className="n" style={{ whiteSpace: 'nowrap' }}>
                  <button className="btn sm ghost" onClick={() => setF({ id: u.id, email: u.email, name: u.name, role: u.role, active: u.active, password: '' })}>Dəyiş</button>
                  {u.id !== me?.id && <button className="btn sm ghost" onClick={() => setRemoving(u)}>Sil</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {f && (
        <FormModal
          title={editing ? 'İstifadəçini dəyiş' : 'Yeni istifadəçi'}
          onClose={() => setF(undefined)}
          onSubmit={async () => {
            const body = { name: f.name, role: f.role, active: f.active, password: f.password || undefined }
            if (editing) await put(`/users/${f.id}`, body)
            else await post('/users', { ...body, email: f.email })
            setF(undefined)
            users.reload()
          }}
        >
          <div className="form" style={{ gridTemplateColumns: '1fr' }}>
            <Field label="E-poçt (giriş üçün)">
              <input type="email" value={f.email} required disabled={editing} onChange={(e) => setF({ ...f, email: e.target.value })} />
            </Field>
            <Field label="Ad soyad"><input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
            <Field label="Rol">
              <select value={f.role} disabled={self} onChange={(e) => setF({ ...f, role: e.target.value as Role })}>
                <option value="ACCOUNTANT">{ROLE_LABEL.ACCOUNTANT}</option>
                <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
              </select>
            </Field>
            <Field label={editing ? 'Yeni parol (boş qalsa dəyişmir)' : 'Parol (ən azı 8 simvol)'}>
              <input type="password" autoComplete="new-password" minLength={8} required={!editing} value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
            </Field>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input type="checkbox" checked={f.active} disabled={self} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Aktiv
            </label>
          </div>
        </FormModal>
      )}
      {removing && (
        <ConfirmModal
          title="İstifadəçini sil"
          onClose={() => setRemoving(undefined)}
          onConfirm={async () => {
            await del(`/users/${removing.id}`)
            setRemoving(undefined)
            users.reload()
          }}
        >
          <b>{removing.name || removing.email}</b> silinəcək və daxil ola bilməyəcək. Uçot məlumatlarına təsir etmir. Müvəqqəti bağlamaq üçün "Passiv" etmək kifayətdir.
        </ConfirmModal>
      )}
    </div>
  )
}

function Password() {
  const [cur, setCur] = useState('')
  const [nw, setNw] = useState('')
  const [rep, setRep] = useState('')
  const save = useSave()
  const [mismatch, setMismatch] = useState(false)
  return (
    <div className="card" style={{ maxWidth: 380 }}>
      <h3>Parolu dəyiş</h3>
      <ErrorBox error={save.err} />
      {mismatch && <div className="error">Yeni parollar eyni deyil</div>}
      {save.msg && <div className="notice">{save.msg}</div>}
      <div className="form" style={{ gridTemplateColumns: '1fr' }}>
        <Field label="Cari parol"><input type="password" value={cur} onChange={(e) => setCur(e.target.value)} /></Field>
        <Field label="Yeni parol"><input type="password" value={nw} onChange={(e) => setNw(e.target.value)} /></Field>
        <Field label="Yeni parol (təkrar)"><input type="password" value={rep} onChange={(e) => setRep(e.target.value)} /></Field>
        <button
          className="btn"
          disabled={save.busy || !cur || !nw}
          onClick={() => {
            setMismatch(nw !== rep)
            if (nw !== rep) return
            save.run(() => post('/auth/password', { current: cur, new: nw }).then(() => { setCur(''); setNw(''); setRep('') }), 'Parol dəyişdirildi')
          }}
        >
          Dəyiş
        </button>
      </div>
    </div>
  )
}

function PaymentCodes() {
  const codes = useAsync(() => get<PaymentCode[]>('/payment-codes'), [])
  const [rows, setRows] = useState<PaymentCode[]>()
  const [seen, setSeen] = useState<PaymentCode[]>()
  const save = useSave()
  if (codes.data && codes.data !== seen) {
    setSeen(codes.data)
    setRows(codes.data)
  }
  if (!rows) return codes.error ? <ErrorBox error={codes.error} /> : <Loading />
  const set = (id: number, patch: Partial<PaymentCode>) => setRows(rows.map((r) => (r.id === id ? { ...r, ...patch } : r)))
  return (
    <div className="card">
      <h3>Ödəniş kodları</h3>
      <p className="muted" style={{ marginTop: 0 }}>Maaş cədvəlindəki "Bank ödənişləri" blokunda istifadə olunur. Passiv kod blokda göstərilmir.</p>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Sıra</th><th>Təyinat</th><th>Büdcə kodu</th><th>Aktiv</th></tr></thead>
          <tbody>
            {[...rows].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id).map((r) => (
              <tr key={r.id}>
                <td><input type="number" style={{ width: 70 }} value={r.sortOrder} onChange={(e) => set(r.id, { sortOrder: Number(e.target.value) })} /></td>
                <td><input style={{ width: '100%', minWidth: 260 }} value={r.title} onChange={(e) => set(r.id, { title: e.target.value })} /></td>
                <td><input style={{ width: 110 }} value={r.budgetCode ?? ''} onChange={(e) => set(r.id, { budgetCode: e.target.value })} /></td>
                <td><input type="checkbox" checked={r.active} onChange={(e) => set(r.id, { active: e.target.checked })} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ErrorBox error={save.err} />
      <div className="actions" style={{ marginTop: 12 }}>
        <button
          className="btn"
          disabled={save.busy}
          onClick={() => save.run(async () => {
            for (const r of rows) await put(`/payment-codes/${r.id}`, { title: r.title, budgetCode: r.budgetCode, active: r.active, sortOrder: r.sortOrder })
            codes.reload()
          })}
        >
          Yadda saxla
        </button>
        {save.msg && <span className="muted">{save.msg}</span>}
      </div>
    </div>
  )
}

function Backup() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  return (
    <div className="card">
      <h3>Ehtiyat nüsxə</h3>
      <ErrorBox error={error} />
      <p>Bütün məlumat (xərclər, satışlar, hesablar, yüklənmiş fayllar və çıxarışlar) bazadadır. Aşağıdakı düymə bazanın təzə tam nüsxəsini bir <b>.dump</b> faylı kimi endirir.</p>
      <div className="actions">
        <button
          className="btn"
          disabled={busy}
          onClick={async () => {
            setBusy(true)
            setError(undefined)
            try { await download('/backup', 'innotex-sade.dump') } catch (e) { setError(e) } finally { setBusy(false) }
          }}
        >
          {busy ? 'Hazırlanır…' : 'Nüsxəni endir'}
        </button>
      </div>
      <div className="explain">
        <b>Bərpa necə edilir.</b> Proqramın qovluğunda <code>docker compose up -d --build</code> ilə proqramı qaldırın, sonra endirilən faylı göstərərək bərpa skriptini işə salın:
        Windows-da <code>scripts\restore.ps1 fayl.dump</code>, Linux-da <code>scripts/restore.sh fayl.dump</code>. Skript proqramı dayandırır, bazanı təmizləyib nüsxədən doldurur və proqramı yenidən başladır.
        Başqa kompüterə köçürmə qaydası qovluqdakı <code>KOCURME.md</code> faylındadır. Avtomatik nüsxələr isə hər gecə 02:30-da <code>backups</code> qovluğuna yazılır (son 30 gün).
        Endirilən faylı kompüterdən kənarda da (flash, bulud) saxlayın.
      </div>
    </div>
  )
}

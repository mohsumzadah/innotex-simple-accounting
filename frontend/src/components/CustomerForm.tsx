import { useState } from 'react'
import { Field, FormModal } from './ui'
import { post, put, type Counterparty } from '../lib/api'
import { BANK, COMPANY, ENTITY, INDIVIDUAL, INDIVIDUAL_BANK, KIND, type EntityType, type Key } from '../lib/counterparty'

/** Sətri müqayisə üçün sadələşdirir: kiçik hərf, azərbaycan hərfləri latın əsasına */
const norm = (s: string) =>
  s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/ə/g, 'e').replace(/ı/g, 'i').replace(/ö/g, 'o').replace(/ü/g, 'u').replace(/ş/g, 's').replace(/ç/g, 'c').replace(/ğ/g, 'g').trim()

/** "Etiket: dəyər" (və ya tab ilə) sətirlərindən kontragent sahələrini çıxarır. Tanınmayan sətirlər buraxılır. */
function parseRequisites(text: string): Partial<Record<Key, string>> {
  const out: Partial<Record<Key, string>> = {}
  for (const raw of text.split(/\r?\n/)) {
    const tab = raw.indexOf('\t')
    const i = tab >= 0 ? tab : raw.indexOf(':')
    if (i < 0) continue
    const label = norm(raw.slice(0, i))
    const value = raw.slice(i + 1).trim()
    if (!value) continue
    let k: Key | undefined
    if (label.includes('voen')) k = label.includes('bank') ? 'bankVoen' : 'voen'
    else if (label.includes('swift')) k = 'swift'
    else if (label.includes('muxbir')) k = 'correspondentAccount'
    else if (label.includes('iban') || label.includes('benefisiar')) k = 'iban'
    else if (label.includes('bank kodu') || label.includes('bic')) k = 'bankCode'
    else if (label.includes('bank ad')) k = 'bank'
    else if (label.includes('direktor')) k = 'director'
    else if (label.includes('unvan')) k = 'address'
    else if (label.includes('telefon')) k = 'phone'
    else if (label.includes('poct') || label.includes('mail')) k = 'email'
    else if (label.includes('huquqi ad') || label === 'ad' || label.includes('teskilat')) k = 'name'
    if (!k) continue
    if (k === 'voen' && out.voen) continue // ilk VÖEN şirkətindir
    out[k] = value
  }
  return out
}

export function CustomerForm({ initial, defaultType, lockType, onClose, onSaved }: { initial?: Counterparty; defaultType?: EntityType; lockType?: boolean; onClose: () => void; onSaved: (c: Counterparty) => void }) {
  const [type, setType] = useState<EntityType>(initial?.entityType ?? defaultType ?? 'LEGAL')
  const [f, setF] = useState<Record<string, string>>(() =>
    Object.fromEntries([...COMPANY, ...BANK, ...INDIVIDUAL, ...INDIVIDUAL_BANK, ['note', '']].map(([k]) => [k, (initial?.[k as Key] as string | null) ?? (k === 'kind' ? 'CUSTOMER' : '')])),
  )
  const [paste, setPaste] = useState('')
  const [filled, setFilled] = useState('')
  const [bankOpen, setBankOpen] = useState(!!(initial?.iban || initial?.cardNumber))
  const individual = type === 'INDIVIDUAL'
  const set = (k: string, v: string) => setF((x) => ({ ...x, [k]: v }))
  const input = (k: Key, extra?: { upper?: boolean; max?: number }) => (
    <input value={f[k]} required={k === 'name'} maxLength={extra?.max} onChange={(e) => set(k, extra?.upper ? e.target.value.toUpperCase() : e.target.value)} />
  )
  const wide = (k: Key) => k === 'name' || k === 'address'
  const cell = ([k, l]: [Key, string]) => (
    <Field key={k} label={l} wide={wide(k)}>
      {k === 'kind' ? (
        <select value={f.kind} onChange={(e) => set('kind', e.target.value)}>
          {Object.entries(KIND).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
        </select>
      ) : k === 'fin' ? input(k, { upper: true, max: 7 }) : input(k)}
    </Field>
  )
  /** Yalnız seçilmiş növə aid sahələr göndərilir */
  const payload = () => {
    const keys: Key[] = (individual ? [...INDIVIDUAL, ...INDIVIDUAL_BANK] : [...COMPANY, ...BANK]).map(([k]) => k)
    const body: Record<string, string> = { entityType: type, note: f.note }
    for (const k of keys) body[k] = f[k]
    return body
  }
  return (
    <FormModal
      title={initial ? 'Kontragenti dəyiş' : 'Yeni kontragent'}
      onClose={onClose}
      onSubmit={async () => {
        if (!individual && f.voen.trim() && !/^\d{10}$/.test(f.voen.trim())) throw new Error('VÖEN 10 rəqəmdən ibarət olmalıdır')
        if (individual && f.fin.trim() && f.fin.trim().length !== 7) throw new Error('FİN 7 simvoldan ibarət olmalıdır')
        const c = initial ? await put<Counterparty>(`/customers/${initial.id}`, payload()) : await post<Counterparty>('/customers', payload())
        onSaved(c)
      }}
    >
      <div className="tabs" role="group" aria-label="Şəxs növü">
        {(Object.keys(ENTITY) as EntityType[]).map((t) => (
          <button key={t} type="button" className={type === t ? 'on' : ''} disabled={lockType && type !== t} onClick={() => setType(t)}>{ENTITY[t]}</button>
        ))}
      </div>
      {!individual && <div className="explain">
        <b>Rekvizitləri yapışdır</b>: "Hüquqi Ad: ...", "VÖEN: ..." kimi sətirləri bura yapışdırın, sahələr avtomatik dolacaq.
        <textarea rows={3} style={{ width: '100%', marginTop: 6 }} value={paste} onChange={(e) => setPaste(e.target.value)} placeholder={'Hüquqi Ad: ...\nBenefisiar hesabı (IBAN): ...\nVÖEN: ...'} />
        <div className="actions" style={{ marginTop: 6 }}>
          <button
            type="button" className="btn ghost sm"
            onClick={() => {
              const p = parseRequisites(paste)
              setF({ ...f, ...(p as Record<string, string>) })
              setFilled(`${Object.keys(p).length} sahə dolduruldu`)
            }}
          >
            Doldur
          </button>
          {filled && <span className="muted">{filled}</span>}
        </div>
      </div>
      }
      <h4>{individual ? 'Fiziki şəxs' : 'Şirkət'}</h4>
      <div className="form">{(individual ? INDIVIDUAL : COMPANY).map(cell)}</div>
      {!individual && (
        <>
          <h4>Bank rekvizitləri</h4>
          <div className="form">
            {BANK.map(([k, l]) => <Field key={k} label={l} wide={k === 'iban'}>{input(k)}</Field>)}
          </div>
        </>
      )}
      {individual && (
        <>
          <h4><button type="button" className="btn ghost sm" onClick={() => setBankOpen(!bankOpen)}>{bankOpen ? '▾' : '▸'} Bank / kart (geri ödəniş üçün, ixtiyari)</button></h4>
          {bankOpen && <div className="form">{INDIVIDUAL_BANK.map(([k, l]) => <Field key={k} label={l} wide>{input(k)}</Field>)}</div>}
        </>
      )}
      <h4>Qeyd</h4>
      <div className="form">
        <Field label="Qeyd" wide><textarea rows={2} value={f.note} onChange={(e) => set('note', e.target.value)} /></Field>
      </div>
    </FormModal>
  )
}

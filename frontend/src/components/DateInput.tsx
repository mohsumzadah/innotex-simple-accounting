import { useRef, useState } from 'react'

type Props = {
  value: string
  onChange: (v: string) => void
  required?: boolean
  disabled?: boolean
  min?: string
  max?: string
  title?: string
  style?: React.CSSProperties
}

/** ISO → dd.mm.yyyy */
const toView = (iso: string) => (/^\d{4}-\d{2}-\d{2}/.test(iso) ? iso.slice(0, 10).split('-').reverse().join('.') : '')

/** Yazılmış mətni ISO-ya çevirir; etibarsızdırsa null. */
function parse(text: string): string | null {
  const t = text.trim()
  let y: number, m: number, d: number
  const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(t)
  if (iso) {
    y = +iso[1]; m = +iso[2]; d = +iso[3]
  } else {
    const r = /^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/.exec(t)
    if (!r) return null
    d = +r[1]; m = +r[2]; y = +r[3]
  }
  const dt = new Date(Date.UTC(y, m - 1, d))
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d || y < 1900 || y > 2200) return null
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

/** Yazarkən nöqtələri avtomatik əlavə edir. */
function mask(raw: string, prev: string): string {
  raw = raw.replace(/[^\d./-]/g, '')
  if (raw.length < prev.length) return raw // silmə
  const digits = raw.replace(/\D/g, '')
  if (raw !== digits) return raw.replace(/[-/]/g, '.') // əl ilə ayırıcı (d.m.yyyy)
  let out = digits.slice(0, 2)
  if (digits.length >= 2) out += '.'
  if (digits.length > 2) out += digits.slice(2, 4)
  if (digits.length >= 4) out += '.'
  if (digits.length > 4) out += digits.slice(4, 8)
  return out
}

const calStyle: React.CSSProperties = { position: 'absolute', right: 0, bottom: 0, width: 1, height: 1, opacity: 0, pointerEvents: 'none', border: 0, padding: 0 }
const btnStyle: React.CSSProperties = { position: 'absolute', right: 4, top: '50%', transform: 'translateY(-50%)', border: 0, background: 'transparent', cursor: 'pointer', padding: '2px 4px', fontSize: 15, lineHeight: 1 }

/** Tarix sahəsi: göstəriş dd.mm.yyyy, dəyər ISO (YYYY-MM-DD) və ya ''. */
export function DateInput({ value, onChange, required, disabled, min, max, title, style }: Props) {
  const [text, setText] = useState(toView(value))
  const [bad, setBad] = useState(false)
  const native = useRef<HTMLInputElement>(null)

  const [prev, setPrev] = useState(value)
  if (prev !== value) {
    setPrev(value)
    if (!(parse(text) === value || (value === '' && text === ''))) setText(toView(value))
    setBad(false)
  }

  function edit(raw: string) {
    const t = mask(raw, text)
    setText(t)
    if (t === '') {
      setBad(false)
      if (value !== '') onChange('')
      return
    }
    const iso = parse(t)
    if (iso !== null && !(min && iso < min) && !(max && iso > max)) {
      setBad(false)
      if (iso !== value) onChange(iso)
    } else setBad(t.length >= 8 || iso !== null)
  }

  function openPicker() {
    const n = native.current
    if (!n || disabled) return
    try {
      n.showPicker()
    } catch {
      n.focus()
    }
  }

  return (
    <span style={{ position: 'relative', display: 'block', ...style }}>
      <input
        type="text"
        inputMode="numeric"
        placeholder="dd.mm.yyyy"
        maxLength={10}
        value={text}
        required={required}
        disabled={disabled}
        title={title}
        aria-invalid={bad || undefined}
        style={{ width: '100%', paddingRight: 28, ...(bad ? { borderColor: 'var(--bad)', outlineColor: 'var(--bad)' } : null) }}
        onChange={(e) => edit(e.target.value)}
        onPaste={(e) => {
          const p = parse(e.clipboardData.getData('text'))
          if (p) {
            e.preventDefault()
            setText(toView(p))
            setBad(false)
            if (p !== value) onChange(p)
          }
        }}
        onBlur={() => {
          if (bad) return
          const iso = parse(text)
          if (iso) setText(toView(iso))
        }}
      />
      <button type="button" style={btnStyle} tabIndex={-1} disabled={disabled} onClick={openPicker} title="Təqvim" aria-label="Təqvim">📅</button>
      <input
        ref={native}
        type="date"
        tabIndex={-1}
        aria-hidden
        value={value}
        min={min}
        max={max}
        style={calStyle}
        onChange={(e) => {
          if (e.target.value) {
            setText(toView(e.target.value))
            setBad(false)
            onChange(e.target.value)
          }
        }}
      />
    </span>
  )
}

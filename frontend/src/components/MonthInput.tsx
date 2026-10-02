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

/** 'YYYY-MM' → mm.yyyy */
const toView = (ym: string) => (/^\d{4}-\d{2}/.test(ym) ? ym.slice(0, 7).split('-').reverse().join('.') : '')

function parse(text: string): string | null {
  const t = text.trim()
  let y: number, m: number
  const r = /^(\d{1,2})[./-](\d{4})$/.exec(t)
  if (r) {
    m = +r[1]; y = +r[2]
  } else {
    const i = /^(\d{4})-(\d{1,2})$/.exec(t)
    if (!i) return null
    y = +i[1]; m = +i[2]
  }
  if (m < 1 || m > 12 || y < 1900 || y > 2200) return null
  return `${y}-${String(m).padStart(2, '0')}`
}

function mask(raw: string, prev: string): string {
  raw = raw.replace(/[^\d./-]/g, '')
  if (raw.length < prev.length) return raw
  const digits = raw.replace(/\D/g, '')
  if (raw !== digits) return raw.replace(/[-/]/g, '.')
  let out = digits.slice(0, 2)
  if (digits.length >= 2) out += '.'
  if (digits.length > 2) out += digits.slice(2, 6)
  return out
}

const calStyle: React.CSSProperties = { position: 'absolute', right: 0, bottom: 0, width: 1, height: 1, opacity: 0, pointerEvents: 'none', border: 0, padding: 0 }
const btnStyle: React.CSSProperties = { position: 'absolute', right: 4, top: '50%', transform: 'translateY(-50%)', border: 0, background: 'transparent', cursor: 'pointer', padding: '2px 4px', fontSize: 15, lineHeight: 1 }

/** Ay sahəsi: göstəriş mm.yyyy, dəyər YYYY-MM və ya ''. */
export function MonthInput({ value, onChange, required, disabled, min, max, title, style }: Props) {
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
    const ym = parse(t)
    if (ym !== null && !(min && ym < min) && !(max && ym > max)) {
      setBad(false)
      if (ym !== value) onChange(ym)
    } else setBad(t.length >= 7 || ym !== null)
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
        placeholder="mm.yyyy"
        maxLength={7}
        value={text}
        required={required}
        disabled={disabled}
        title={title}
        aria-invalid={bad || undefined}
        style={{ width: '100%', paddingRight: 28, ...(bad ? { borderColor: 'var(--bad)', outlineColor: 'var(--bad)' } : null) }}
        onChange={(e) => edit(e.target.value)}
        onBlur={() => {
          if (bad) return
          const ym = parse(text)
          if (ym) setText(toView(ym))
        }}
      />
      <button type="button" style={btnStyle} tabIndex={-1} disabled={disabled} onClick={openPicker} title="Təqvim" aria-label="Təqvim">📅</button>
      <input
        ref={native}
        type="month"
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

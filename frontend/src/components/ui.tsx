import { useState, type FormEvent, type ReactNode } from 'react'
import { download, uploadFile } from '../lib/api'

export function Warnings({ items }: { items?: string[] }) {
  if (!items?.length) return null
  return (
    <div className="warnbox" role="alert">
      <b>Diqqət: hesabat natamamdır</b>
      <ul>{items.map((w) => <li key={w}>{w}</li>)}</ul>
    </div>
  )
}

export function Badge({ tone = 'muted', children }: { tone?: 'ok' | 'muted' | 'warn' | 'bad' | 'info'; children: ReactNode }) {
  return <span className={`badge b-${tone}`}>{children}</span>
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null
  return <div className="error">{error instanceof Error ? error.message : String(error)}</div>
}

export function Loading() {
  return <p className="muted">Yüklənir…</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="muted">{children}</p>
}

export function PageHead({ title, sub, children }: { title: string; sub?: string; children?: ReactNode }) {
  return (
    <div className="head" style={{ marginBottom: 14 }}>
      <div>
        <h1>{title}</h1>
        {sub && <div className="sub" style={{ marginBottom: 0 }}>{sub}</div>}
      </div>
      <div className="actions">{children}</div>
    </div>
  )
}

export function Stat({ k, v, tone }: { k: string; v: ReactNode; tone?: 'ok' | 'bad' | 'warn' }) {
  return (
    <div className="card">
      <div className="k">{k}</div>
      <div className="v" style={{ color: tone ? `var(--${tone})` : undefined }}>{v}</div>
    </div>
  )
}

export function Modal({ title, onClose, children, xl }: { title: string; onClose: () => void; children: ReactNode; xl?: boolean }) {
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${xl ? " xl" : ""}`} role="dialog" aria-label={title}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  )
}

/** Forma: submit zamanı düymə bağlanır, xəta formanın üstündə göstərilir; xəta olarsa açıq qalır */
export function FormModal({
  title, submitText = 'Yadda saxla', danger, onClose, onSubmit, children,
}: {
  title: string; submitText?: string; danger?: boolean; onClose: () => void; onSubmit: () => Promise<void>; children?: ReactNode
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await onSubmit()
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }
  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={submit}>
        <ErrorBox error={error} />
        {children}
        <div className="actions">
          <button type="button" className="btn ghost" onClick={onClose}>İmtina</button>
          <button type="submit" className={`btn${danger ? ' danger' : ''}`} disabled={busy}>{busy ? 'Gözləyin…' : submitText}</button>
        </div>
      </form>
    </Modal>
  )
}

/** Təsdiq pəncərəsi (brauzerin confirm() əvəzinə): nə silindiyini göstərir, xəta pəncərənin içində çıxır */
export function ConfirmModal({ title, children, submitText = 'Sil', onClose, onConfirm }: {
  title: string; children?: ReactNode; submitText?: string; onClose: () => void; onConfirm: () => Promise<void>
}) {
  return (
    <FormModal title={title} submitText={submitText} danger onClose={onClose} onSubmit={onConfirm}>
      <div className="muted" style={{ marginBottom: 12 }}>{children}</div>
    </FormModal>
  )
}

export function Field({ label, wide, children }: { label: string; wide?: boolean; children: ReactNode }) {
  return (
    <label className={`f${wide ? ' wide' : ''}`}>
      <span>{label}</span>
      {children}
    </label>
  )
}

/** Asinxron əməliyyat düyməsi (Excel, sənəd açmaq və s.); xətanı yanında göstərir */
export function ActionButton({ onRun, children, ghost = true, sm }: { onRun: () => Promise<void>; children: ReactNode; ghost?: boolean; sm?: boolean }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  return (
    <span className="actions">
      <button
        type="button"
        className={`btn${ghost ? ' ghost' : ''}${sm ? ' sm' : ''}`}
        disabled={busy}
        onClick={async () => {
          setBusy(true)
          setError(undefined)
          try {
            await onRun()
          } catch (e) {
            setError(e)
          } finally {
            setBusy(false)
          }
        }}
      >
        {busy ? 'Gözləyin…' : children}
      </button>
      {error ? <span style={{ color: 'var(--bad)', fontSize: 12 }}>{error instanceof Error ? error.message : 'Xəta'}</span> : null}
    </span>
  )
}

/** Fayl yükləmə sahəsi: mövcud fayl varsa yükləmə linki, yenisini seçəndə POST /api/files */
export function FileField({ label, fileId, onChange }: { label: string; fileId: number | null | undefined; onChange: (id: number | null) => void }) {
  const [name, setName] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  return (
    <Field label={label}>
      <input
        type="file"
        disabled={busy}
        onChange={async (e) => {
          const f = e.target.files?.[0]
          if (!f) return
          setBusy(true)
          setError(undefined)
          try {
            const r = await uploadFile(f)
            setName(r.name)
            onChange(r.id)
          } catch (err) {
            setError(err)
          } finally {
            setBusy(false)
          }
        }}
      />
      {busy && <span className="muted">Yüklənir…</span>}
      {error ? <span style={{ color: 'var(--bad)', fontSize: 12 }}>{error instanceof Error ? error.message : 'Xəta'}</span> : null}
      {fileId ? (
        <span className="muted">
          {name ? `${name} · ` : ''}
          <FileLink id={fileId} />{' '}
          <a href="#" onClick={(e) => { e.preventDefault(); setName(undefined); onChange(null) }}>sil</a>
        </span>
      ) : null}
    </Field>
  )
}

/** Hərəkət/xərc sətrində çıxarışa kiçik keçid (faylı endirir) */
export function StatementLink({ id }: { id: number | null | undefined }) {
  const [err, setErr] = useState('')
  if (!id) return null
  return (
    <>
      <a
        href="#"
        title="Bu hərəkətin çıxarışı"
        onClick={async (e) => {
          e.preventDefault()
          try {
            await download(`/statements/${id}/file`, `cixaris-${id}`)
            setErr('')
          } catch (x) {
            setErr(x instanceof Error ? x.message : 'Xəta')
          }
        }}
      >
        çıxarış
      </a>
      {err && <span style={{ color: 'var(--bad)' }}> {err}</span>}
    </>
  )
}

export function FileLink({ id, text = 'Faylı endir' }: { id: number | null | undefined; text?: string }) {
  const [err, setErr] = useState('')
  if (!id) return <span className="muted">—</span>
  return (
    <>
      <a
        href="#"
        onClick={async (e) => {
          e.preventDefault()
          try {
            await download(`/files/${id}`, `fayl-${id}`)
            setErr('')
          } catch (x) {
            setErr(x instanceof Error ? x.message : 'Xəta')
          }
        }}
      >
        {text}
      </a>
      {err && <span style={{ color: 'var(--bad)' }}> {err}</span>}
    </>
  )
}

/** 📋 düyməsi: yalnız verilən mətni panoya kopyalayır və qısa "Kopyalandı" göstərir. Boş dəyər üçün heç nə göstərmir. */
/** Mətnin özü kopyalanır: üzərinə klik → buferə; display göstərilən, text kopyalanan dəyərdir */
export function Copy({ text, children }: { text: string | null | undefined; children?: ReactNode }) {
  const [done, setDone] = useState(false)
  if (!text) return <>{children ?? <span className="muted">—</span>}</>
  return (
    <span
      role="button"
      tabIndex={0}
      className={`copyable ${done ? "done" : ""}`}
      title="Kopyalamaq üçün klikləyin"
      onClick={async (e) => {
        e.stopPropagation()
        try {
          await navigator.clipboard.writeText(text)
          setDone(true)
          setTimeout(() => setDone(false), 1200)
        } catch { /* clipboard icazəsi yoxdur */ }
      }}
      onKeyDown={(e) => { if (e.key === "Enter") (e.currentTarget as HTMLElement).click() }}
    >
      {children ?? text}
      {done && <span className="copied">✓ kopyalandı</span>}
    </span>
  )
}

export function CopyBtn({ text }: { text: string | null | undefined }) {
  const [done, setDone] = useState(false)
  if (!text) return null
  return (
    <button
      type="button"
      className={`copy ${done ? 'done' : ''}`}
      title="Kopyala"
      onClick={async (e) => {
        e.stopPropagation()
        try {
          await navigator.clipboard.writeText(text)
          setDone(true)
          setTimeout(() => setDone(false), 1500)
        } catch { /* clipboard icazəsi yoxdur */ }
      }}
    >
      {done ? '✓ Kopyalandı' : '📋'}
    </button>
  )
}

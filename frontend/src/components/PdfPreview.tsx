import { useEffect, useState } from 'react'
import { fetchBlob } from '../lib/api'
import { ErrorBox } from './ui'

/** PDF faylını token ilə çəkib iframe-də (blob URL) göstərir */
export function PdfPreview({ path }: { path: string }) {
  const [url, setUrl] = useState<string>()
  const [err, setErr] = useState<unknown>()

  useEffect(() => {
    let dead = false
    let made: string | undefined
    fetchBlob(path)
      .then((blob) => {
        if (dead) return
        made = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
        setUrl(made)
      })
      .catch((e) => !dead && setErr(e))
    return () => {
      dead = true
      if (made) URL.revokeObjectURL(made)
    }
  }, [path])

  return (
    <>
      <ErrorBox error={err} />
      {!url && !err && <div className="muted">Yüklənir…</div>}
      {url && <iframe title="PDF" src={url} style={{ width: '100%', height: '70vh', border: '1px solid var(--line)', borderRadius: 6 }} />}
    </>
  )
}

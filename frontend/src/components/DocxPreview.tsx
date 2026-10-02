import { useEffect, useRef, useState } from 'react'
import { renderAsync } from 'docx-preview'
import { fetchBlob } from '../lib/api'
import { DOCX_OPTIONS } from '../lib/docx'
import { ErrorBox } from './ui'


/** Word (.docx) faylını token ilə çəkib A4 vərəqləri kimi göstərir (boz fonda kölgəli səhifələr) */
export function DocxPreview({ path }: { path: string }) {
  const box = useRef<HTMLDivElement>(null)
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(true)

  useEffect(() => {
    let dead = false
    fetchBlob(path)
      .then(async (blob) => {
        if (dead || !box.current) return
        box.current.innerHTML = ''
        await renderAsync(blob, box.current, undefined, DOCX_OPTIONS)
      })
      .catch((e) => !dead && setErr(e))
      .finally(() => !dead && setBusy(false))
    return () => {
      dead = true
    }
  }, [path])

  return (
    <>
      <ErrorBox error={err} />
      {busy && <div className="muted">Yüklənir…</div>}
      <div ref={box} className="docx-view" />
    </>
  )
}


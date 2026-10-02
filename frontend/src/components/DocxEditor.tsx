import { useRef, useState } from 'react'
import { download, openPdf, post, postFile, put, type DealDocument } from '../lib/api'
import { DOC_CODE, label } from '../lib/format'
import { printDocx } from '../lib/docx'
import { DocxPreview } from './DocxPreview'
import { PdfPreview } from './PdfPreview'
import { ActionButton, ErrorBox, Field, Modal } from './ui'

/** Word (.docx) sənədinin redaktoru: önizləmə + endir / düzəlişli faylı yüklə / yenidən doldur / çap / geri qayıt */
export function DocxEditor({ doc: initial, onClose, onChanged }: { doc: DealDocument; onClose: () => void; onChanged: () => void }) {
  const [doc, setDoc] = useState(initial)
  const [title, setTitle] = useState(initial.title)
  const [err, setErr] = useState<unknown>()
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  const [ver, setVer] = useState(0)
  const pick = useRef<HTMLInputElement>(null)

  async function run(fn: () => Promise<void>) {
    setBusy(true)
    setErr(undefined)
    setMsg('')
    try {
      await fn()
    } catch (e) {
      setErr(e)
    } finally {
      setBusy(false)
    }
  }
  const apply = (d: DealDocument, m: string) => {
    setDoc(d)
    setTitle(d.title)
    setVer((v) => v + 1)
    setMsg(m)
    onChanged()
  }
  const docxPath = `/deal-documents/${doc.id}/docx`
  const pdf = doc.format === 'PDF'

  return (
    <Modal xl title={`${label(DOC_CODE, doc.code)}: ${pdf ? 'PDF' : 'Word'} sənədi`} onClose={onClose}>
      <ErrorBox error={err} />
      {msg && <div className="notice">{msg}</div>}
      <div className="form" style={{ marginBottom: 10 }}>
        <Field label="Sənədin adı" wide>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onBlur={() => {
              if (title.trim() && title !== doc.title) run(async () => { setDoc(await put<DealDocument>(`/deal-documents/${doc.id}`, { title })); onChanged() })
            }}
          />
        </Field>
      </div>
      <div className="toolbar">
        <ActionButton onRun={() => download(docxPath, `${doc.title}.${pdf ? 'pdf' : 'docx'}`)}>{pdf ? 'Endir' : 'Word-da endir'}</ActionButton>
        <button className="btn ghost sm" disabled={busy} onClick={() => pick.current?.click()}>{pdf ? 'Yeni versiya yüklə' : 'Düzəlişli faylı yüklə'}</button>
        <input
          ref={pick}
          type="file"
          accept={pdf ? '.pdf' : '.docx'}
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            e.target.value = ''
            if (f) run(async () => apply(await postFile<DealDocument>(`/deal-documents/${doc.id}/upload`, f), 'Düzəlişli fayl yükləndi'))
          }}
        />
        {doc.templateId != null && <button
          className="btn danger sm"
          disabled={busy}
          onClick={() => {
            if (!confirm('Şablondan yenidən doldurulsun? Cari fayl əvəz olunacaq (əvvəlki versiyaya qayıtmaq mümkün qalır).')) return
            run(async () => apply(await post<DealDocument>(`/deal-documents/${doc.id}/refill`), 'Şablondan yenidən dolduruldu'))
          }}
        >
          Şablondan yenidən doldur
        </button>}
        <ActionButton onRun={() => (pdf ? openPdf(docxPath) : printDocx(docxPath))}>{pdf ? 'Çap' : 'Çap / PDF'}</ActionButton>
        {doc.hasPrevious && (
          <button
            className="btn ghost sm"
            disabled={busy}
            onClick={() => {
              if (!confirm('Əvvəlki versiyaya qayıdılsın?')) return
              run(async () => apply(await post<DealDocument>(`/deal-documents/${doc.id}/revert`), 'Əvvəlki versiyaya qayıdıldı'))
            }}
          >
            Əvvəlki versiyaya qayıt
          </button>
        )}
      </div>
      {pdf ? <PdfPreview key={ver} path={docxPath} /> : <DocxPreview key={ver} path={docxPath} />}
      <div className="actions">
        <button className="btn ghost" onClick={onClose}>Bağla</button>
      </div>
      <p className="muted">Sənədi Word-da endirib düzəldin, sonra "Düzəlişli faylı yüklə" ilə geri yükləyin. "Çap / PDF" yeni pəncərədə çap dialoqunu açır, orada PDF kimi saxlaya bilərsiniz.</p>
    </Modal>
  )
}

import { useEffect, useRef, useState } from 'react'
import { del, download, get, openDocument, openPdf, post, postFile, put, qs, type Deal, type DealDocument, type DealPayments, type Stage, type Template } from '../lib/api'
import { DOC_CODE, fmtDate, fmtDateTime, label, STAGE } from '../lib/format'
import { useAsync } from '../lib/useAsync'
import { DocxEditor } from './DocxEditor'
import { printDocx } from '../lib/docx'
import { ActionButton, Empty, ErrorBox, Field, FileLink, FormModal, Modal } from './ui'

/** Yaradılan sənədin redaktoru: WYSIWYG (contentEditable) + HTML mənbə rejimi */
function DocEditor({ doc, onClose, onChanged }: { doc: DealDocument; onClose: () => void; onChanged: () => void }) {
  const [title, setTitle] = useState(doc.title)
  const [html, setHtml] = useState(doc.html ?? '')
  const [source, setSource] = useState(false)
  const [err, setErr] = useState<unknown>()
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  const box = useRef<HTMLDivElement>(null)

  // WYSIWYG sahəsinə HTML yalnız rejim dəyişəndə / yenidən doldurulanda yazılır (yazarkən kursor sıçramasın)
  useEffect(() => {
    if (!source && box.current) box.current.innerHTML = html
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source, doc.id])

  const current = () => (!source && box.current ? box.current.innerHTML : html)
  const exec = (cmd: string, val?: string) => {
    box.current?.focus()
    document.execCommand(cmd, false, val)
  }
  function deleteBlock() {
    const root = box.current
    const sel = window.getSelection()
    if (!root || !sel?.anchorNode) return
    let n: Node | null = sel.anchorNode
    while (n && n.parentNode !== root) n = n.parentNode
    if (n && n.parentNode === root) n.parentNode.removeChild(n)
  }
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
  const save = () =>
    run(async () => {
      const h = current()
      await put(`/deal-documents/${doc.id}`, { title, html: h })
      setHtml(h)
      setMsg('Yadda saxlanıldı')
      onChanged()
    })
  const refill = () =>
    run(async () => {
      if (!confirm('Şablondan yenidən doldurulsun? Əl ilə etdiyiniz dəyişikliklər itəcək.')) return
      const d = await post<DealDocument>(`/deal-documents/${doc.id}/refill`)
      setTitle(d.title)
      setHtml(d.html ?? '')
      if (box.current && !source) box.current.innerHTML = d.html ?? ''
      setMsg('Şablondan yenidən dolduruldu')
      onChanged()
    })

  return (
    <Modal xl title={`${label(DOC_CODE, doc.code)}: redaktə`} onClose={onClose}>
      <ErrorBox error={err} />
      {msg && <div className="notice">{msg}</div>}
      <div className="form" style={{ marginBottom: 10 }}>
        <Field label="Sənədin adı" wide><input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      </div>
      <div className="toolbar">
        {!source && (
          <>
            <button type="button" className="btn ghost sm" onClick={() => exec('bold')}><b>B</b></button>
            <button type="button" className="btn ghost sm" onClick={() => exec('italic')}><i>I</i></button>
            <button type="button" className="btn ghost sm" onClick={() => exec('formatBlock', 'h3')}>Başlıq</button>
            <button type="button" className="btn ghost sm" onClick={() => exec('formatBlock', 'p')}>Mətn</button>
            <button type="button" className="btn ghost sm" onClick={() => exec('insertOrderedList')}>1. Nömrəli siyahı</button>
            <button type="button" className="btn ghost sm" onClick={() => exec('insertUnorderedList')}>• Siyahı</button>
            <button type="button" className="btn ghost sm" onClick={() => exec('insertHTML', '<p>Yeni bənd</p>')}>+ Bənd əlavə et</button>
            <button type="button" className="btn ghost sm" onClick={deleteBlock}>− Bəndi sil</button>
          </>
        )}
        <button
          type="button"
          className="btn ghost sm"
          style={{ marginLeft: 'auto' }}
          onClick={() => {
            const h = current()
            setHtml(h)
            setSource(!source)
          }}
        >
          {source ? 'Vizual rejim' : 'HTML'}
        </button>
      </div>
      {source ? (
        <textarea className="code" value={html} onChange={(e) => setHtml(e.target.value)} spellCheck={false} />
      ) : (
        <div ref={box} className="editor" contentEditable suppressContentEditableWarning />
      )}
      <div className="actions" style={{ justifyContent: 'space-between' }}>
        <div className="actions">
          <button className="btn danger" disabled={busy} onClick={refill}>Şablondan yenidən doldur</button>
          <ActionButton onRun={async () => { await save(); await openDocument(`/deal-documents/${doc.id}/print`) }}>Çap / PDF</ActionButton>
        </div>
        <div className="actions">
          <button className="btn ghost" onClick={onClose}>Bağla</button>
          <button className="btn" disabled={busy} onClick={save}>Yadda saxla</button>
        </div>
      </div>
      <p className="muted">"Çap / PDF" əvvəlcə yadda saxlayır, sonra çap səhifəsini açır. Brauzerdə "Çap et" seçib PDF kimi saxlaya bilərsiniz.</p>
    </Modal>
  )
}

const FORMAT = { DOCX: 'Word', PDF: 'PDF', HTML: 'Sənəd' } as const

function openDoc(d: DealDocument) {
  return d.format === 'DOCX' ? printDocx(`/deal-documents/${d.id}/docx`) : d.format === 'PDF' ? openPdf(`/deal-documents/${d.id}/docx`) : openDocument(`/deal-documents/${d.id}/print`)
}

/**
 * Bir mərhələnin sənəd bloku (müqavilə / protokol / akt). Sənədlər yalnız öz mərhələsində yaradılır, yüklənir və silinir.
 * Dəyişiklikdən sonra onChanged çağrılır (satış və sənəd siyahısı yenilənir).
 */
export function DocBlock({ dealId, code, docs, onChanged, autoOpen }: { dealId: number; code: string; docs: DealDocument[]; onChanged: () => void; autoOpen?: boolean }) {
  const mine = docs.filter((d) => d.code === code)
  const [edit, setEdit] = useState<DealDocument>()
  // yeni satışdan gələndə (startContract) sənəd redaktoru/önizləməsi bir dəfə avtomatik açılır
  const opened = useRef(false)
  useEffect(() => {
    if (autoOpen && !opened.current && mine.length) {
      opened.current = true
      setEdit(mine[0])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoOpen, mine.length])
  const [picking, setPicking] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [renaming, setRenaming] = useState<{ id: number; title: string }>()
  async function rename() {
    if (!renaming?.title.trim()) return
    try {
      await put(`/deal-documents/${renaming.id}`, { title: renaming.title.trim() })
      setRenaming(undefined)
      onChanged()
    } catch (e) {
      setErr(e)
    }
  }
  const [err, setErr] = useState<unknown>()
  const verRef = useRef<HTMLInputElement>(null)
  const verFor = useRef<DealDocument>(undefined)

  async function newVersion(f: File) {
    try {
      await postFile<DealDocument>(`/deal-documents/${verFor.current!.id}/upload`, f)
      onChanged()
    } catch (e) {
      setErr(e)
    }
  }

  return (
    <div className="wide" style={{ gridColumn: '1 / -1' }}>
      <div className="muted" style={{ marginBottom: 6 }}>{label(DOC_CODE, code)} sənədi</div>
      <ErrorBox error={err} />
      {!mine.length ? (
        <div className="actions">
          <button type="button" className="btn ghost" onClick={() => setPicking(true)}>Şablondan yarat</button>
          <button type="button" className="btn ghost" onClick={() => setUploading(true)}>Hazır sənədi yüklə</button>
          <span className="muted">Əvvəlcə mərhələni yadda saxlayın ki, nömrə və tarix sənədə düşsün.</span>
        </div>
      ) : (
        <table>
          <tbody>
            {mine.map((d) => (
              <tr key={d.id}>
                <td>
                  {renaming?.id === d.id ? (
                    <div className="actions">
                      <input
                        autoFocus
                        style={{ flex: 1, minWidth: 180 }}
                        value={renaming.title}
                        onChange={(e) => setRenaming({ id: d.id, title: e.target.value })}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') { e.preventDefault(); void rename() }
                          if (e.key === 'Escape') setRenaming(undefined)
                        }}
                      />
                      <button type="button" className="btn sm" disabled={!renaming.title.trim()} onClick={() => void rename()}>Yadda saxla</button>
                      <button type="button" className="btn ghost sm" onClick={() => setRenaming(undefined)}>İmtina</button>
                    </div>
                  ) : (
                    <>
                      <b>{d.title}</b>{' '}
                      <button type="button" className="btn ghost sm" title="Sənədin adını dəyiş" onClick={() => setRenaming({ id: d.id, title: d.title })}>✎ Adını dəyiş</button>
                    </>
                  )}
                  <div className="muted">{FORMAT[d.format]} · {fmtDateTime(d.updatedAt)}</div>
                </td>
                <td className="n">
                  <div className="actions" style={{ justifyContent: 'flex-end' }}>
                    <button type="button" className="btn ghost sm" onClick={() => setEdit(d)}>Aç / redaktə</button>
                    <ActionButton sm onRun={() => openDoc(d)}>Çap / PDF</ActionButton>
                    {d.format !== 'HTML' && (
                      <button type="button" className="btn ghost sm" onClick={() => { verFor.current = d; verRef.current?.click() }}>Yeni versiya yüklə</button>
                    )}
                    <button
                      type="button"
                      className="btn ghost sm"
                      onClick={async () => {
                        if (!confirm('Sənəd silinsin?')) return
                        try {
                          await del(`/deal-documents/${d.id}`)
                          onChanged()
                        } catch (e) {
                          setErr(e)
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
      {mine.length > 0 && (
        <div className="actions" style={{ marginTop: 6 }}>
          <button type="button" className="btn ghost sm" onClick={() => setUploading(true)}>+ Əlavə sənəd yüklə</button>
          <button type="button" className="btn ghost sm" onClick={() => setPicking(true)}>+ Şablondan əlavə et</button>
          <span className="muted">Məs. {code === 'CONTRACT' ? 'müqaviləyə' : 'sənədə'} əlavə ayrıca sənəd kimi (.docx / .pdf).</span>
        </div>
      )}
      <input
        ref={verRef}
        type="file"
        hidden
        accept=".docx,.pdf"
        onChange={(e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (f) void newVersion(f)
        }}
      />
      {picking && <NewDoc dealId={dealId} code={code} onClose={() => setPicking(false)} onCreated={(d) => { setPicking(false); onChanged(); setEdit(d) }} />}
      {uploading && <UploadDoc dealId={dealId} code={code} extra={mine.length} onClose={() => setUploading(false)} onCreated={(d) => { setUploading(false); onChanged(); setEdit(d) }} />}
      {edit && (edit.format !== 'HTML'
        ? <DocxEditor key={edit.id} doc={edit} onClose={() => setEdit(undefined)} onChanged={onChanged} />
        : <DocEditor key={edit.id} doc={edit} onClose={() => setEdit(undefined)} onChanged={onChanged} />)}
    </div>
  )
}

function NewDoc({ dealId, code, onClose, onCreated }: { dealId: number; code: string; onClose: () => void; onCreated: (d: DealDocument) => void }) {
  const [tid, setTid] = useState('')
  const tpls = useAsync(() => get<Template[]>('/templates' + qs({ code })), [code])
  const list = tpls.data ?? []
  const chosen = tid && list.some((t) => String(t.id) === tid) ? tid : String((list.find((t) => t.isDefault) ?? list[0])?.id ?? '')
  return (
    <FormModal
      title={`${label(DOC_CODE, code)}: şablondan yarat`}
      submitText="Yarat"
      onClose={onClose}
      onSubmit={async () => {
        if (!chosen) throw new Error('Bu növ üçün şablon yoxdur. Ayarlar → Şablonlar bölməsində əlavə edin.')
        onCreated(await post<DealDocument>(`/deals/${dealId}/documents`, { code, templateId: Number(chosen) }))
      }}
    >
      <div className="form">
        <Field label="Şablon" wide>
          <select value={chosen} onChange={(e) => setTid(e.target.value)}>
            {list.map((t) => <option key={t.id} value={t.id}>{t.title}{t.isDefault ? ' (əsas)' : ''}</option>)}
          </select>
        </Field>
      </div>
    </FormModal>
  )
}

/** Hazır (şablonsuz) sənədi yükləyir: .docx və ya .pdf. extra > 0: mərhələdə artıq sənəd var, bu onun əlavəsidir */
function UploadDoc({ dealId, code, extra = 0, onClose, onCreated }: { dealId: number; code: string; extra?: number; onClose: () => void; onCreated: (d: DealDocument) => void }) {
  const [title, setTitle] = useState(extra > 0 ? `${code === 'CONTRACT' ? 'Müqaviləyə' : `${label(DOC_CODE, code)} sənədinə`} əlavə №${extra}` : '')
  const [file, setFile] = useState<File>()
  return (
    <FormModal
      title={`${label(DOC_CODE, code)}: ${extra > 0 ? 'əlavə sənəd yüklə' : 'hazır sənədi yüklə'}`}
      submitText="Yüklə"
      onClose={onClose}
      onSubmit={async () => {
        if (!file) throw new Error('Fayl seçin (.docx və ya .pdf)')
        onCreated(await postFile<DealDocument>(`/deals/${dealId}/documents/upload`, file, { code, title: title.trim() }))
      }}
    >
      <div className="form">
        <Field label="Ad (boşdursa fayl adı)"><input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
        <Field label="Fayl (.docx və ya .pdf)" wide><input type="file" accept=".docx,.pdf" onChange={(e) => setFile(e.target.files?.[0])} /></Field>
      </div>
    </FormModal>
  )
}

interface FileRow { key: string; stage: string; title: string; date: string | null; open?: () => Promise<void>; dl?: () => Promise<void>; fileId?: number | null }

/** Satışın bütün faylları (yalnız oxumaq üçün): sənədlər, qaimə/akt faylları, ödəniş tapşırıqları */
export function DealFiles({ deal, docs, payments }: { deal: Deal; docs: DealDocument[]; payments?: DealPayments }) {
  const rows: FileRow[] = []
  for (const d of docs) {
    rows.push({
      key: `d${d.id}`, stage: STAGE[d.code as Stage] ?? d.code, title: `${d.title} (${FORMAT[d.format]})`, date: d.updatedAt, open: () => openDoc(d),
      dl: d.format === 'HTML' ? undefined : () => download(`/deal-documents/${d.id}/docx`, d.title),
    })
  }
  if (deal.advanceFileId) rows.push({ key: 'adv', stage: STAGE.ADVANCE_INVOICE, title: `Avans qaiməsi ${deal.advanceInvoiceNo ?? ''}`.trim(), date: deal.advanceInvoiceDate, fileId: deal.advanceFileId })
  if (deal.actFileId) rows.push({ key: 'act', stage: STAGE.ACT, title: `İmzalı akt ${deal.actNo ?? ''}`.trim(), date: deal.actDate, fileId: deal.actFileId })
  if (deal.invoiceFileId) rows.push({ key: 'inv', stage: STAGE.INVOICE, title: `Qaimə ${deal.invoiceNo ?? ''}`.trim(), date: deal.invoiceDate, fileId: deal.invoiceFileId })
  for (const p of payments?.items ?? []) if (p.fileId) rows.push({ key: `p${p.id}`, stage: 'Ödəniş', title: 'Ödəniş tapşırığı', date: p.date, fileId: p.fileId })
  return (
    <div className="card">
      <h3>Satışın faylları</h3>
      {!rows.length ? <Empty>Hələ fayl yoxdur. Sənədlər və fayllar öz mərhələsindən əlavə olunur.</Empty> : (
        <table>
          <thead><tr><th>Mərhələ</th><th>Fayl</th><th>Tarix</th><th /></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key}>
                <td>{r.stage}</td>
                <td>{r.title}</td>
                <td className="muted" style={{ whiteSpace: 'nowrap' }}>{r.date ? (r.date.length > 10 ? fmtDateTime(r.date) : fmtDate(r.date)) : '—'}</td>
                <td className="n">
                  <div className="actions" style={{ justifyContent: 'flex-end' }}>
                    {r.open && <ActionButton sm onRun={r.open}>Aç</ActionButton>}
                    {r.dl && <ActionButton sm onRun={r.dl}>Endir</ActionButton>}
                    {r.fileId ? <FileLink id={r.fileId} text="Endir" /> : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

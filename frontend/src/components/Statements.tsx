import { useState } from 'react'
import { DateInput } from './DateInput'
import { Empty, ErrorBox, Field, FileLink, FormModal } from './ui'
import { del, get, postFile, type Statement } from '../lib/api'
import { fmtDate, today } from '../lib/format'
import { useAsync } from '../lib/useAsync'

function UploadForm({ accountId, onClose, onSaved }: { accountId: number; onClose: () => void; onSaved: () => void }) {
  const [file, setFile] = useState<File>()
  const [from, setFrom] = useState(today())
  const [to, setTo] = useState(today())
  const [note, setNote] = useState('')
  return (
    <FormModal
      title="Çıxarış yüklə"
      onClose={onClose}
      onSubmit={async () => {
        if (!file) throw new Error('Fayl seçilməyib')
        await postFile(`/accounts/${accountId}/statements`, file, { periodFrom: from, periodTo: to, note })
        onSaved()
      }}
    >
      <div className="form">
        <Field label="Çıxarış faylı (xlsx, pdf …)" wide><input type="file" required onChange={(e) => setFile(e.target.files?.[0])} /></Field>
        <Field label="Dövr: başlanğıc"><DateInput value={from} required onChange={setFrom} /></Field>
        <Field label="Dövr: son"><DateInput value={to} required onChange={setTo} /></Field>
        <Field label="Qeyd" wide><input value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      </div>
    </FormModal>
  )
}

/** Hesabın çıxarışları: siyahı, endirmə, yükləmə, silmə (bağlı hərəkət varsa 409 göstərilir) */
export function StatementsBlock({ accountId, onChanged }: { accountId: number; onChanged?: () => void }) {
  const list = useAsync(() => get<Statement[]>(`/accounts/${accountId}/statements`), [accountId])
  const [upload, setUpload] = useState(false)
  const [err, setErr] = useState<unknown>()
  return (
    <div className="card">
      <div className="head">
        <h3>Çıxarışlar</h3>
        <div className="actions"><button className="btn sm" onClick={() => setUpload(true)}>Çıxarış yüklə</button></div>
      </div>
      <ErrorBox error={list.error ?? err} />
      {!list.data?.length ? <Empty>Çıxarış yoxdur.</Empty> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Dövr</th><th>Fayl</th><th>Qeyd</th><th className="n">Bağlı hərəkət</th><th /></tr></thead>
            <tbody>
              {list.data.map((s) => (
                <tr key={s.id}>
                  <td>{fmtDate(s.periodFrom)} – {fmtDate(s.periodTo)}</td>
                  <td><FileLink id={s.fileId} text={s.fileName} /></td>
                  <td>{s.note}</td>
                  <td className="n">{s.movementCount}</td>
                  <td className="n">
                    <button
                      className="btn ghost sm"
                      onClick={async () => {
                        if (!confirm('Çıxarış silinsin?')) return
                        try { setErr(undefined); await del(`/statements/${s.id}`); list.reload(); onChanged?.() } catch (e) { setErr(e) }
                      }}
                    >
                      Sil
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {upload && <UploadForm accountId={accountId} onClose={() => setUpload(false)} onSaved={() => { setUpload(false); list.reload(); onChanged?.() }} />}
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { DealFiles, DocBlock } from '../components/DealDocs'
import { NewDeal } from '../components/NewDeal'
import { DateInput } from '../components/DateInput'
import { Badge, ErrorBox, Field, FileField, Loading, PageHead } from '../components/ui'
import { ProductSelect, QuantityField } from '../components/ProductSelect'
import { del, get, post, put, type Deal, type DealDocument, type DealEvent, type DealPayments, type PaymentTerms, type Product, type Stage } from '../lib/api'
import { fmtDate, fmtDateTime, money, num, scaleByQuantity, STAGE, today } from '../lib/format'
import { LinkMovementModal, NewIncomeModal, PaymentsCard, PayStatusBadge, TermsFields } from '../components/Payments'
import { useAsync } from '../lib/useAsync'
import { StageBadge } from './Deals'

type F = Record<string, string | number | null>

/** Hər mərhələnin öz sahələri (deal-dakı sahə adları ilə eynidir) */
function initialFields(d: Deal, stage: Stage): F {
  switch (stage) {
    case 'CONTRACT': return { contractNo: d.contractNo ?? '', contractDate: d.contractDate ?? d.saleDate ?? today() }
    case 'PROTOCOL': return { protocolDate: d.protocolDate ?? today() }
    case 'ADVANCE_INVOICE':
      return { advanceInvoiceNo: d.advanceInvoiceNo ?? '', advanceInvoiceDate: d.advanceInvoiceDate ?? today(), advanceAmount: d.advanceAmount ?? (num(d.advanceRequired) > 0 ? d.advanceRequired : d.price), advanceFileId: d.advanceFileId }
    case 'ACT': return { actNo: d.actNo ?? '', actDate: d.actDate ?? today(), actFileId: d.actFileId }
    case 'INVOICE':
      return { invoiceNo: d.invoiceNo ?? '', invoiceDate: d.invoiceDate ?? today(), invoiceAmount: d.invoiceAmount ?? d.price, invoiceFileId: d.invoiceFileId }
    default: return {}
  }
}

const HINT: Partial<Record<Stage, string>> = {
  CONTRACT: 'Müqavilə nömrəsini boş buraxsanız, avtomatik veriləcək (INX-İL-PS-001).',
  PROTOCOL: 'Bu mərhələ istəyə bağlıdır, lazım deyilsə ötürün.',
  ADVANCE_INVOICE: 'Məbləğ default olaraq qiymət × avans faizidir, dəyişə bilərsiniz. Avans qaiməsi yoxdursa, ödənişi birbaşa "Ödənişlər" kartında bağlaya bilərsiniz.',
  PAID: 'Ödəniş addımı göstəricidir: ödənişlər bank mədaxilinə bağlanır və bu addım avtomatik tamamlanır. Ödəniş tapşırığı faylı ödənişin özündə saxlanılır.',
  IN_PROGRESS: 'Proqram hazırlanır. Əlavə məlumat tələb olunmur.',
  ACT: 'Akt nömrəsini boş buraxsanız, avtomatik veriləcək (AKT-İL-001); sənəd şablondan yaradılanda da nömrə və tarix dolur.',
  INVOICE: 'Gəlir bu qaimənin tarixində tanınır (hesabata düşür).',
}

const RETAIL_HINT: Partial<Record<Stage, string>> = {
  NEW: 'Fiziki şəxs: müqavilə, akt və e-qaimə yoxdur. Ödəniş (ABB linki / kart köçürməsi) alınanda çeki, ekran şəklini və ya bank tranzaksiyasını "Ödənişlər" kartında bağlayın.',
  PAID: 'Ödəniş və çek alınıb. Quraşdırma istəyə bağlıdır: lazım deyilsə ötürün.',
  IN_PROGRESS: 'Lisenziya quraşdırılır. Bitəndən sonra satışı tamamlayın: gəlir tamamlanma tarixində tanınır.',
}

/** "Növbəti addım": cari mərhələyə görə əsas əməliyyat (panel = mərhələ panelini aç, complete = NEW → müqavilə, pay = ödəniş bağla) */
function nextAction(d: Deal, docs: DealDocument[]): { label: string; text: string; kind: 'panel' | 'complete' | 'pay' | 'none' } {
  if (d.paymentTerms === 'RETAIL' && d.stage !== 'DONE') {   // fiziki şəxs: NEW → PAID → (Quraşdırma) → DONE
    if (d.stage === 'NEW') {
      return num(d.remaining) > 0
        ? { label: 'Ödənişi bağla', text: `Çek, ekran şəkli və ya bank tranzaksiyasını bağlayın (qalıq ${money(d.remaining)}).`, kind: 'pay' }
        : { label: 'Ödəniş alındı', text: 'Ödəniş tamdır, mərhələni tamamlayın.', kind: 'panel' }
    }
    if (d.stage === 'PAID') return { label: 'Quraşdırmanı tamamla', text: 'Lisenziya quraşdırılandan sonra satışı tamamlayın (quraşdırma istəyə bağlıdır).', kind: 'panel' }
    return { label: 'Satışı tamamla', text: 'Quraşdırma bitəndən sonra satışı tamamlayın: gəlir bu tarixdə hesabata düşür.', kind: 'panel' }
  }
  const has = (code: string) => docs.some((x) => x.code === code)
  const step = d.stepper.find((s) => s.current)
  const nextKey = d.stepper.slice(d.stepper.findIndex((s) => s.current) + 1).find((s) => s.key !== 'FINAL_PAYMENT')?.key
  switch (d.stage) {
    case 'NEW': return { label: 'Müqaviləni hazırla', text: 'Müqavilə nömrəsi verilir və əsas şablondan müqavilə hazırlanır.', kind: 'complete' }
    case 'CONTRACT':
      return has('CONTRACT')
        ? { label: 'Müqaviləni tamamla', text: 'Müqaviləni yoxlayın, imzalanandan sonra mərhələni tamamlayın.', kind: 'panel' }
        : { label: 'Müqaviləni hazırla', text: 'Müqavilə sənədi hələ yoxdur: şablondan yaradın və ya hazır faylı yükləyin.', kind: 'panel' }
    case 'PROTOCOL':
      return { label: has('PROTOCOL') ? 'Protokolu tamamla' : 'Protokolu hazırla', text: 'Qiymət protokolu istəyə bağlıdır, lazım deyilsə ötürün.', kind: 'panel' }
    case 'ADVANCE_INVOICE':
      return { label: 'Avans qaiməsini qeyd et', text: `Avans qaiməsinin nömrəsi və tarixi (avans: ${money(d.advanceRequired)}).`, kind: 'panel' }
    case 'PAID':
      return step?.done && nextKey === 'DONE'
        ? { label: 'Satışı bağla', text: 'Ödəniş tamdır, satışı tamamlayın.', kind: 'panel' }
        : { label: 'Ödənişi bağla', text: `Müştərinin bank ödənişini satışa bağlayın (qalıq ${money(d.remaining)}).`, kind: 'pay' }
    case 'IN_PROGRESS': return { label: 'Quraşdırmanı tamamla', text: 'Proqram quraşdırılandan sonra aktı hazırlamağa keçin.', kind: 'panel' }
    case 'ACT':
      return has('ACT')
        ? { label: 'İmzalı aktı yüklə', text: 'Aktı müştəriyə göndərin, imzalı nüsxəni yükləyib mərhələni tamamlayın.', kind: 'panel' }
        : { label: 'Aktı hazırla', text: 'Təhvil-təslim aktını şablondan yaradın (nömrə avtomatik verilir).', kind: 'panel' }
    case 'INVOICE': return { label: 'Qaiməni qeyd et', text: 'Təsdiqlənmiş e-qaimənin nömrəsini, tarixini və faylını daxil edin.', kind: 'panel' }
    default:
      return num(d.remaining) > 0
        ? { label: 'Ödənişi bağla', text: `Satış tamamlanıb, lakin qalıq ${money(d.remaining)} ödənilməyib.`, kind: 'pay' }
        : { label: 'Satış tamamlanıb', text: 'Növbəti addım yoxdur.', kind: 'none' }
  }
}

export function DealView() {
  const { id } = useParams()
  const nav = useNavigate()
  const location = useLocation()
  const autoOpenContract = (location.state as { open?: string } | null)?.open === 'CONTRACT'
  const stageRef = useRef<HTMLDivElement>(null)
  const [repeat, setRepeat] = useState(false)
  const [toast, setToast] = useState('')
  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(''), 4500)
    return () => clearTimeout(t)
  }, [toast])
  const deal = useAsync(() => get<Deal>(`/deals/${id}`), [id])
  const events = useAsync(() => get<DealEvent[]>(`/deals/${id}/events`), [id])
  const products = useAsync(() => get<Product[]>('/products?active=true'), [])
  const [payKey, setPayKey] = useState(0)
  const docs = useAsync(() => get<DealDocument[]>(`/deals/${id}/documents`), [id])
  const pays = useAsync(() => get<DealPayments>(`/deals/${id}/payments`), [id, payKey])
  const d = deal.data
  const retail = d?.paymentTerms === 'RETAIL'

  const [selected, setSelected] = useState<Stage>()
  const [fields, setFields] = useState<F>({})
  const payRef = useRef<HTMLDivElement>(null)
  const [payModal, setPayModal] = useState<'link' | 'new'>()
  const [note, setNote] = useState('')
  const [eventDate, setEventDate] = useState(today())
  const [error, setError] = useState<unknown>()
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [info, setInfo] = useState<Deal>()
  const [infoBusy, setInfoBusy] = useState(false)
  const [infoSaved, setInfoSaved] = useState(false)
  const [qty, setQty] = useState('1')

  const stage = selected ?? d?.stage
  // sahələr yalnız mərhələ və ya onların dəyərləri dəyişəndə yenilənir (sənəd yüklənməsi yazılmamış dəyişikliyi silməsin)
  const sig = d && stage ? JSON.stringify(initialFields(d, stage)) : ''
  const curStage = d?.stage
  useEffect(() => {
    if (d && stage) {
      setFields(initialFields(d, stage))
      setNote('')
      setSaved(false)
      // cari mərhələ: bu gün; keçilmiş mərhələ: tarixçədəki tarix
      const past = stage === d.stage ? undefined : events.data?.find((e) => e.toStage === stage)
      setEventDate(past?.date ?? today())
      setError(undefined)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sig, stage, curStage, events.data])
  useEffect(() => {
    setInfo(d)
    if (d) setQty(String(d.quantity ?? 1))
  }, [d])

  if (deal.loading && !d) return <Loading />
  if (!d || !stage) return <ErrorBox error={deal.error} />

  const reloadAll = () => {
    deal.reload()
    events.reload()
    docs.reload()
    setPayKey((k) => k + 1)
  }
  const paymentsChanged = () => {
    deal.reload()
    setPayKey((k) => k + 1)
  }
  const docsChanged = () => {
    deal.reload()
    docs.reload()
  }
  const set = (k: string, v: string | number | null) => setFields((f) => ({ ...f, [k]: v }))
  const text = (k: string, l: string, type = 'text') => (
    <Field label={l}>
      {type === 'date' ? <DateInput value={(fields[k] as string) ?? ''} onChange={(v) => set(k, v)} /> : <input type={type} step={type === 'number' ? '0.01' : undefined} value={(fields[k] as string) ?? ''} onChange={(e) => set(k, e.target.value)} />}
    </Field>
  )
  const file = (k: string, l: string) => <FileField label={l} fileId={fields[k] as number | null} onChange={(v) => set(k, v)} />

  const curIdx = d.stepper.findIndex((s) => s.current)
  const stepIdx = d.stepper.findIndex((s) => s.key === stage)
  const step = d.stepper[stepIdx]
  const isCur = stage === d.stage
  const isPast = !isCur && (stepIdx < curIdx || !!step?.done)
  const isFuture = !isCur && !isPast
  // növbəti real mərhələ (FINAL_PAYMENT göstəricidir)
  const nextStep = curIdx < 0 ? undefined : d.stepper.slice(curIdx + 1).find((s) => s.key !== 'FINAL_PAYMENT')
  const completeText = !nextStep ? 'Tamamla' : nextStep.key === 'DONE' ? 'Satışı tamamla' : `Tamamla → ${nextStep.label}`

  function body(withDate: boolean) {
    const b: Record<string, unknown> = { stage, note, ...(withDate ? { eventDate } : {}), ...fields }
    for (const k of Object.keys(b)) if (b[k] === '') b[k] = null
    return b
  }

  /** Bütün mərhələ əməliyyatları: cavabdan sonra satış yenilənir və seçim cari mərhələyə keçir */
  async function run(path: string, payload: Record<string, unknown>, move: boolean) {
    setBusy(true)
    setError(undefined)
    setSaved(false)
    try {
      const next = await post<Deal>(`/deals/${d!.id}/${path}`, payload)
      reloadAll()
      if (move) {
        setSelected(next.stage)
        if (path === 'complete' || path === 'skip') {
          setToast(`Növbəti: ${nextAction(next, docs.data ?? []).label}`)
          setTimeout(() => stageRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150)
        }
      } else setSaved(true)
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  const save = () => run('fields', body(!isCur), false)
  const complete = () => {
    if (stage === 'PAID' && step && !step.done && !confirm('Ödəniş hələ tam alınmayıb. Yenə də növbəti mərhələyə keçilsin?')) return
    return run('complete', body(true), true)
  }
  const skip = () => run('skip', { note, eventDate }, true)
  const jump = () => {
    if (!confirm(`"${STAGE[stage!]}" mərhələsinə birbaşa keçilsin? Aradakı mərhələlər tamamlanmamış qalacaq.`)) return
    return run('stage', { stage, eventDate: today() }, true)
  }

  /** Cari mərhələnin əsas düyməsi: panel açılır / NEW-dən müqavilə başlayır / ödəniş bağlama pəncərəsi */
  async function doNext() {
    const na = nextAction(d!, docs.data ?? [])
    if (na.kind === 'complete') {
      setBusy(true)
      setError(undefined)
      try {
        await post<Deal>(`/deals/${d!.id}/complete`, { contractDate: d!.saleDate })
        await post(`/deals/${d!.id}/documents`, { code: 'CONTRACT' })
        reloadAll()
        setSelected('CONTRACT')
        setToast('Müqavilə hazırlandı')
        setTimeout(() => stageRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 150)
      } catch (e) {
        setError(e)
        reloadAll()
      } finally {
        setBusy(false)
      }
      return
    }
    setSelected(d!.stage)
    if (na.kind === 'pay') setPayModal('link')
    stageRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function saveInfo() {
    if (!info) return
    setInfoBusy(true)
    setInfoSaved(false)
    setError(undefined)
    try {
      await put<Deal>(`/deals/${d!.id}`, {
        customerId: info.customerId, productId: info.productId ?? 0, product: info.product, description: info.description,
        price: info.price, computers: info.computers, quantity: Number(qty) >= 1 ? Number(qty) : undefined, note: info.note, saleDate: info.saleDate || undefined,
        paymentTerms: info.paymentTerms, advancePercent: info.paymentTerms === 'PARTIAL' ? info.advancePercent : undefined,
      })
      // qiymət dəyişə bilər: "Ödənişlər" kartı (qiymət/qalıq) və fayllar da yenidən oxunur
      reloadAll()
      setInfoSaved(true)
    } catch (e) {
      setError(e)
    } finally {
      setInfoBusy(false)
    }
  }

  const docBlock = (code: string) => <DocBlock dealId={d.id} code={code} docs={docs.data ?? []} onChanged={docsChanged} autoOpen={autoOpenContract && code === 'CONTRACT'} />
  const na = nextAction(d, docs.data ?? [])
  const payStage = stage === 'PAID' || (retail && stage === 'NEW')
  const hasFields = ['CONTRACT', 'PROTOCOL', 'ADVANCE_INVOICE', 'ACT', 'INVOICE'].includes(stage)

  return (
    <>
      <PageHead title={d.customerName} sub={`${d.product}${d.productCode ? ` (${d.productCode})` : ''}${d.quantity > 1 ? ` · ${d.quantity} ədəd` : ''} · ${money(d.price)} · ${d.computers} kompüter`}>
        <button className="btn ghost" onClick={() => setRepeat(true)}>Təkrar satış</button>
        {d.cancelled ? (
          <button
            className="btn ghost"
            onClick={async () => {
              try {
                await post<Deal>(`/deals/${d.id}/restore`); reloadAll()
              } catch (e) {
                setError(e)
              }
            }}
          >
            Bərpa et
          </button>
        ) : (
          <button
            className="btn ghost"
            onClick={async () => {
              const reason = prompt(d.hasAllocations ? 'Satış ləğv olunsun? Bağlı ödənişlər satışa bağlı qalacaq. Səbəb (ixtiyari):' : 'Satış ləğv olunsun? Səbəb (ixtiyari):')
              if (reason === null) return
              try {
                await post<Deal>(`/deals/${d.id}/cancel`, { reason }); reloadAll()
              } catch (e) {
                setError(e)
              }
            }}
          >
            Ləğv et
          </button>
        )}
        <button
          className="btn danger"
          disabled={d.hasAllocations}
          title={d.hasAllocations ? 'Əvvəlcə ödəniş bağlantılarını ayırın' : undefined}
          onClick={async () => {
            if (!confirm('Satış silinsin? Satışın sənədləri və tarixçəsi də silinəcək. Bu əməliyyat geri qaytarılmır.')) return
            try {
              await del(`/deals/${d.id}`)
              nav('/deals')
            } catch (e) {
              setError(e)
            }
          }}
        >
          Satışı sil
        </button>
      </PageHead>
      <p className="muted"><Link to="/deals">← Bütün satışlar</Link></p>
      {d.cancelled && (
        <div className="card" style={{ borderColor: 'var(--bad)' }}>
          <Badge tone="bad">ləğv olunub</Badge> Bu satış hesabatlara, idarə panelinə və lövhəyə daxil deyil{d.cancelReason ? `. Səbəb: ${d.cancelReason}` : ''}.
          {d.hasAllocations && <div className="muted">Bağlı bank ödənişləri satışa bağlı qalır. Silmək üçün əvvəl bağlantıları ayırın.</div>}
        </div>
      )}
      <div className="card deal-summary">
        <div className="line">
          <b>{d.customerName}</b>
          <span>{d.product}</span>
          <b>{money(d.price)}</b>
          <span><PayStatusBadge status={d.paymentStatus} /> <span className="muted">ödənilib {money(d.paid)} · qalıq {money(d.remaining)}</span></span>
          <span className="muted">Mərhələ: <StageBadge stage={d.stage} /></span>
        </div>
        <div className="next">
          <span className="muted">Növbəti addım:</span>
          <span>{na.text}</span>
          {na.kind !== 'none' && <button className="btn sm" disabled={busy} onClick={doNext}>{na.label}</button>}
        </div>
      </div>
      <ErrorBox error={error} />
      {repeat && (
        <NewDeal
          onClose={() => setRepeat(false)}
          initial={{
            customerId: d.customerId, productId: d.productId, product: d.product, description: d.description, price: d.price,
            computers: d.computers, quantity: d.quantity, paymentTerms: d.paymentTerms, advancePercent: d.advancePercent,
          }}
        />
      )}
      {toast && <div className="toast" role="status">{toast}</div>}

      <div className="stepper">
        {d.stepper.map((s, i) => {
          const isPay = s.key === 'PAID' || s.key === 'FINAL_PAYMENT'
          return (
            <button
              key={s.key}
              className={`${s.done ? 'done' : ''} ${s.current ? 'current' : ''} ${s.optional ? 'optional' : ''}`}
              style={s.key === stage && !s.current ? { borderColor: 'var(--accent)', borderWidth: 2 } : undefined}
              onClick={() => {
                if (s.key !== 'FINAL_PAYMENT') setSelected(s.key as Stage)
                if (isPay) payRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
              }}
              title={s.optional ? 'İstəyə bağlı, ötürə bilərsiniz' : isPay ? 'Ödəniş göstəricisi: ödəniş bağlandıqda avtomatik tamamlanır' : undefined}
            >
              <span className="n">{s.done ? '✓' : i + 1}</span>
              {s.label}
            </button>
          )
        })}
      </div>

      <div className="card" ref={stageRef} style={{ scrollMarginTop: 120 }}>
        <h3>{d.stepper.find((s) => s.key === stage)?.label ?? STAGE[stage]}{isCur ? ' (cari mərhələ)' : ''}</h3>
        {isFuture && <p className="muted" style={{ marginTop: 0 }}>Bu mərhələ hələ başlamayıb.</p>}
        {isPast && (() => {
          const ev = events.data?.find((e) => e.toStage === stage)
          return ev ? <p className="muted" style={{ marginTop: 0 }}>Mərhələ tarixi: {ev.date ? fmtDate(ev.date) : fmtDateTime(ev.createdAt)}</p> : null
        })()}
        {isCur && (retail ? RETAIL_HINT[stage] : HINT[stage]) && <p className="muted" style={{ marginTop: 0 }}>{retail ? RETAIL_HINT[stage] : HINT[stage]}</p>}
        <fieldset disabled={isFuture || busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
          <div className="form">
            {stage === 'CONTRACT' && <>{text('contractNo', 'Müqavilə №')}{text('contractDate', 'Müqavilə tarixi', 'date')}{docBlock('CONTRACT')}</>}
            {stage === 'PROTOCOL' && <>{text('protocolDate', 'Protokol tarixi', 'date')}{docBlock('PROTOCOL')}</>}
            {stage === 'ADVANCE_INVOICE' && (
              <>{text('advanceInvoiceNo', 'Avans qaiməsi №')}{text('advanceInvoiceDate', 'Tarix', 'date')}{text('advanceAmount', 'Məbləğ (₼)', 'number')}{file('advanceFileId', 'Qaimə faylı')}</>
            )}
            {payStage && (
              <div className="wide" style={{ gridColumn: '1 / -1' }}>
                <PayStatusBadge status={d.paymentStatus} /> <span className="muted">ödənilib {money(d.paid)}, qalıq {money(d.remaining)}
                  {num(d.advanceRequired) > 0 ? ` · avans: ${money(d.advancePaid)} / ${money(d.advanceRequired)}` : ''}</span>{' '}
                <button type="button" className="btn ghost sm" onClick={() => setPayModal('link')}>Bank ödənişini bağla</button>
              </div>
            )}
            {stage === 'ACT' && <>{text('actNo', 'Akt №')}{text('actDate', 'Akt tarixi', 'date')}{docBlock('ACT')}{file('actFileId', 'İmzalı akt')}</>}
            {stage === 'INVOICE' && (
              <>{text('invoiceNo', 'Qaimə №')}{text('invoiceDate', 'Qaimə tarixi', 'date')}{text('invoiceAmount', 'Məbləğ (₼)', 'number')}{file('invoiceFileId', 'Təsdiqlənmiş e-qaimə')}</>
            )}
            {!hasFields && !payStage && <p className="muted wide" style={{ gridColumn: '1 / -1', margin: 0 }}>{stage === 'DONE' ? 'Satış tamamlanıb.' : 'Bu mərhələdə doldurulacaq sahə yoxdur.'}</p>}
            {isPast && <Field label="Mərhələ tarixi"><DateInput value={eventDate} onChange={setEventDate} /></Field>}
            {!isFuture && <Field label="Qeyd (tarixçəyə yazılır)" wide><input value={note} onChange={(e) => setNote(e.target.value)} /></Field>}
          </div>
        </fieldset>
        {isCur && (
          <div className="actions" style={{ marginTop: 12 }}>
            {nextStep && <Field label="Mərhələ tarixi"><DateInput value={eventDate} onChange={setEventDate} /></Field>}
            {nextStep && <button className="btn" disabled={busy} onClick={complete}>{completeText}</button>}
            {step?.optional && nextStep && <button className="btn ghost" disabled={busy} onClick={skip}>Ötür</button>}
            <button className="btn ghost" disabled={busy} onClick={save}>Yadda saxla</button>
            {saved && <span className="muted">Yadda saxlanıldı</span>}
          </div>
        )}
        {isPast && (
          <div className="actions" style={{ marginTop: 12 }}>
            <button className="btn" disabled={busy} onClick={save}>Yadda saxla</button>
            {saved && <span className="muted">Yadda saxlanıldı</span>}
          </div>
        )}
        {isFuture && (
          <div className="actions" style={{ marginTop: 12 }}>
            <button className="btn ghost sm" disabled={busy} onClick={jump}>Birbaşa bu mərhələyə keç</button>
          </div>
        )}
      </div>

      <div ref={payRef}>
        <PaymentsCard retail={retail} deal={d} reloadKey={payKey} onLink={() => setPayModal('link')} onNew={() => setPayModal('new')} onChanged={paymentsChanged} />
      </div>
      {payModal === 'link' && <LinkMovementModal deal={d} onClose={() => setPayModal(undefined)} onSaved={() => { setPayModal(undefined); paymentsChanged() }} />}
      {payModal === 'new' && <NewIncomeModal deal={d} onClose={() => setPayModal(undefined)} onSaved={() => { setPayModal(undefined); paymentsChanged() }} />}

      <DealFiles deal={d} docs={docs.data ?? []} payments={pays.data} />

      <div className="two">
        <div className="card">
          <h3>Satış məlumatı</h3>
          {info && (
            <div className="form">
              <ProductSelect
                products={products.data ?? []}
                productId={info.productId}
                product={info.product}
                currentName={d.product}
                onText={(v) => setInfo({ ...info, product: v })}
                onPick={(p) => {
                  // nomenklaturanın qiyməti və kompüter sayı 1 vahid üçündür: miqdara vurulur
                  const q = Number(qty) >= 1 ? Number(qty) : 1
                  setInfo(p ? {
                    ...info, productId: p.id, productCode: p.code, product: p.name,
                    price: p.defaultPrice ? scaleByQuantity(p.defaultPrice, 1, 1, q).price : info.price,
                    computers: p.defaultComputers ? p.defaultComputers * q : info.computers, description: p.description ?? info.description,
                  } : { ...info, productId: null, productCode: null })
                }}
              />
              <Field label="Satış tarixi"><DateInput value={info.saleDate ?? ''} onChange={(v) => setInfo({ ...info, saleDate: v })} /></Field>
              <QuantityField
                quantity={qty}
                unit={products.data?.find((p) => p.id === info.productId)?.unit}
                onChange={(q, from, to) => {
                  setQty(q)
                  if (from && to) setInfo((x) => x && { ...x, ...scaleByQuantity(x.price, x.computers, from, to) })
                }}
              />
              <Field label="Qiymət (₼)"><input type="number" step="0.01" value={info.price} onChange={(e) => setInfo({ ...info, price: e.target.value })} /></Field>
              <Field label="Kompüter sayı"><input type="number" min="1" value={info.computers} onChange={(e) => setInfo({ ...info, computers: Number(e.target.value) })} /></Field>
              <TermsFields price={info.price} terms={info.paymentTerms} percent={info.advancePercent} onChange={(t, p) => setInfo({ ...info, paymentTerms: t as PaymentTerms, advancePercent: p })} />
              <Field label="Təsvir" wide><textarea rows={2} value={info.description ?? ''} onChange={(e) => setInfo({ ...info, description: e.target.value })} /></Field>
              <Field label="Qeyd" wide><textarea rows={2} value={info.note ?? ''} onChange={(e) => setInfo({ ...info, note: e.target.value })} /></Field>
            </div>
          )}
          <div className="actions" style={{ marginTop: 10 }}>
            <button className="btn ghost" disabled={infoBusy} onClick={saveInfo}>Yadda saxla</button>
            {infoSaved && <span className="muted">Yadda saxlanıldı</span>}
          </div>
        </div>
        <div className="card">
          <h3>Tarixçə</h3>
          {events.data?.length ? (
            <table>
              <tbody>
                {events.data.map((e, i) => (
                  <tr key={e.id ?? i}>
                    <td className="muted" style={{ whiteSpace: 'nowrap' }}>{e.date ? fmtDate(e.date) : fmtDateTime(e.createdAt)}</td>
                    <td>
                      {e.fromStage ? `${STAGE[e.fromStage]} → ` : ''}<b>{STAGE[e.toStage]}</b>
                      {e.note && <div className="muted">{e.note}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : <p className="muted">Hələ hadisə yoxdur.</p>}
        </div>
      </div>
    </>
  )
}

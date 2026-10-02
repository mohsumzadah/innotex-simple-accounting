import type { Stage } from './api'

/** "1 234.56 ₼" */
export function money(v: string | number | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const [i, d] = Math.abs(n).toFixed(2).split('.')
  return `${n < 0 ? '-' : ''}${i.replace(/\B(?=(\d{3})+(?!\d))/g, ' ')}.${d} ₼`
}

export const num = (v: string | number | null | undefined) => Number(v ?? 0) || 0

export const MONTHS = ['Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'İyun', 'İyul', 'Avqust', 'Sentyabr', 'Oktyabr', 'Noyabr', 'Dekabr']
export function monthName(ym: string) {
  const [y, m] = ym.split('-')
  return `${MONTHS[Number(m) - 1] ?? m} ${y}`
}

/** ISO tarix (YYYY-MM-DD) → dd.mm.yyyy */
export const fmtDate = (d: string | null | undefined) => (d ? d.slice(0, 10).split('-').reverse().join('.') : '—')

const bakuFmt = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Baku', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})
/** ISO tarix-vaxt → dd.mm.yyyy HH:mm (Asia/Baku). Yalnız tarix gəlsə dd.mm.yyyy. */
export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  if (iso.length <= 10) return fmtDate(iso)
  const t = new Date(/([zZ]|[+-]\d\d:?\d\d)$/.test(iso) ? iso : iso + 'Z')
  if (Number.isNaN(t.getTime())) return iso
  const p: Record<string, string> = {}
  for (const x of bakuFmt.formatToParts(t)) p[x.type] = x.value
  return `${p.day}.${p.month}.${p.year} ${p.hour}:${p.minute}`
}
/** 'YYYY-MM' → mm.yyyy */
export const fmtMonth = (ym: string | null | undefined) => (ym ? ym.slice(0, 7).split('-').reverse().join('.') : '—')
export const today = () => new Date().toISOString().slice(0, 10)
export const thisMonth = () => today().slice(0, 7)
export function currentQuarter() {
  const d = new Date()
  return { year: d.getFullYear(), q: Math.floor(d.getMonth() / 3) + 1 }
}

export const STAGES: Stage[] = ['NEW', 'CONTRACT', 'PROTOCOL', 'ADVANCE_INVOICE', 'PAID', 'IN_PROGRESS', 'ACT', 'INVOICE', 'DONE']
export const STAGE: Record<Stage, string> = {
  NEW: 'Yeni müştəri',
  CONTRACT: 'Müqavilə',
  PROTOCOL: 'Qiymət protokolu',
  ADVANCE_INVOICE: 'Avans qaiməsi',
  PAID: 'Ödəniş alındı',
  IN_PROGRESS: 'İcra',
  ACT: 'Təhvil-təslim aktı',
  INVOICE: 'Qaimə',
  DONE: 'Tamamlandı',
}
export const TERMS: Record<string, string> = { PREPAID: '100% avans', PARTIAL: 'Qismən avans', POSTPAID: 'Sonradan ödəniş', RETAIL: 'Fiziki şəxs (pərakəndə)' }
export const PAY_STATUS: Record<string, string> = { UNPAID: 'Ödənilməyib', PARTIAL: 'Qismən ödənilib', PAID: 'Ödənilib', OVERPAID: 'Artıq ödənilib' }
export const KIND: Record<string, string> = { ADVANCE: 'Avans', FINAL: 'Yekun / qalıq', OTHER: 'Digər' }
export const PURPOSE: Record<string, string> = {
  CUSTOMER_PAYMENT: 'Müştəri ödənişi',
  FOUNDER_LOAN: 'Təsisçinin faizsiz yardımı',
  CHARTER_CAPITAL: 'Nizamnamə kapitalı',
  EXPENSE: 'Xərc ödənişi',
  SALARY: 'Maaş',
  TAX: 'Vergi / sığorta ödənişi',
  BANK_FEE: 'Bank komissiyası',
  TRANSFER: 'Hesablar arası köçürmə',
  OWNER_REPAYMENT: 'Direktora qaytarış',
  OTHER: 'Digər',
}
export const ACCOUNT_TYPE: Record<string, string> = { BANK: 'Bank hesabı', DIRECTOR_CARD: 'Direktorun şəxsi kartı', CASH: 'Kassa' }
export const CATEGORIES = [
  'Server/Hosting', 'AI/Proqram təminatı', 'Abunəlik', 'Marketinq/Reklam', 'Rabitə', 'Ofis/İcarə',
  'Nəqliyyat', 'Avadanlıq', 'Bank xərci', 'Dövlət rüsumu', 'Digər',
]
export const WORKPLACE: Record<string, string> = { MAIN: 'Əsas iş yeri', SECONDARY: 'Əlavə iş yeri' }

export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? (m[k] ?? k) : '—')
export const DOC_CODE: Record<string, string> = { CONTRACT: 'Müqavilə', PROTOCOL: 'Protokol', ACT: 'Akt' }

/** Kartın bitmə tarixi (YYYY-AA, ayın son günü) üçün qalan gün; mənfi = bitib. */
export function cardDaysLeft(ym: string | null | undefined): number | null {
  if (!ym) return null
  const [y, m] = ym.split('-').map(Number)
  const end = new Date(y, m, 0)
  const t = new Date()
  return Math.round((end.getTime() - new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime()) / 86400000)
}

/** Miqdar dəyişəndə qiyməti və kompüter sayını mütənasib yeniləyir (vahid = əvvəlki miqdar üçün dəyər) */
export function scaleByQuantity(price: string, computers: number, from: number, to: number) {
  return {
    price: price === '' ? '' : (Math.round((Number(price) || 0) / from * to * 100) / 100).toFixed(2),
    computers: Math.max(1, Math.round(computers / from * to)),
  }
}

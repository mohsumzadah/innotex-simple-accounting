// Backend ilə müqavilə: SPEC.md "API". Pul dəyərləri string-dir.
const TOKEN_KEY = 'sade.token'

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (t: string | null) => (t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY))

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export type Role = 'ADMIN' | 'ACCOUNTANT'
export const ROLE_LABEL: Record<Role, string> = { ADMIN: 'Administrator', ACCOUNTANT: 'Mühasib' }
export interface Me { id: number; email: string; name: string; role: Role }
export interface AppUser { id: number; email: string; name: string; role: Role; active: boolean; createdAt: string | null }

let onUnauthorized: () => void = () => {}
export const setUnauthorizedHandler = (fn: () => void) => {
  onUnauthorized = fn
}

async function raw(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && typeof init.body === 'string') headers.set('Content-Type', 'application/json')
  let res: Response
  try {
    res = await fetch('/api' + path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Serverlə əlaqə yoxdur')
  }
  if (!res.ok) {
    let msg = 'Xəta baş verdi'
    try {
      const j = await res.json()
      if (j?.message) msg = j.message
    } catch {
      /* boş cavab */
    }
    if (res.status === 401 && !path.startsWith('/auth/login')) {
      setToken(null)
      onUnauthorized()
      msg = 'Sessiya bitib, yenidən daxil olun'
    }
    throw new ApiError(res.status, msg)
  }
  return res
}

async function json<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await raw(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const get = <T>(p: string) => json<T>(p)
export const post = <T>(p: string, b?: unknown) => json<T>(p, 'POST', b ?? {})
export const put = <T>(p: string, b?: unknown) => json<T>(p, 'PUT', b ?? {})
export const del = (p: string) => json<void>(p, 'DELETE')
/** multipart/form-data (Content-Type brauzer tərəfindən sərhədlə qoyulur) */
export async function postForm<T>(p: string, form: FormData): Promise<T> {
  const res = await raw(p, { method: 'POST', body: form })
  return (await res.json()) as T
}

export const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== '')
  return p.length ? '?' + p.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&') : ''
}

async function blobOf(path: string) {
  const res = await raw(path)
  return { blob: await res.blob(), cd: res.headers.get('Content-Disposition') }
}

function filenameFrom(cd: string | null, fallback: string) {
  const m = cd && /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd)
  if (!m) return fallback
  try {
    return decodeURIComponent(m[1])
  } catch {
    return m[1]
  }
}

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

/** Token ilə yükləyib faylı endirir (xlsx, əlavə fayllar) */
export async function download(path: string, fallbackName: string) {
  const { blob, cd } = await blobOf(path)
  saveBlob(blob, filenameFrom(cd, fallbackName))
}

/** HTML sənədi token ilə çəkib yeni vərəqdə açır (çap üçün) */
export async function openDocument(path: string) {
  const w = window.open('', '_blank')
  try {
    const { blob } = await blobOf(path)
    const url = URL.createObjectURL(new Blob([blob], { type: 'text/html;charset=utf-8' }))
    if (w) w.location.href = url
    else window.location.href = url
  } catch (e) {
    w?.close()
    throw e
  }
}

/** PDF-i token ilə çəkib yeni vərəqdə açır (brauzerin PDF görüntüləyicisindən çap olunur) */
export async function openPdf(path: string) {
  const w = window.open('', '_blank')
  try {
    const blob = await fetchBlob(path)
    const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
    if (w) w.location.href = url
    else window.location.href = url
  } catch (e) {
    w?.close()
    throw e
  }
}

/** Token ilə faylı blob kimi çəkir (Word önizləmə üçün) */
export async function fetchBlob(path: string): Promise<Blob> {
  return (await blobOf(path)).blob
}

/** multipart "file" ilə POST edib JSON cavabı qaytarır */
export async function postFile<T>(path: string, file: File, fields?: Record<string, string>): Promise<T> {
  const fd = new FormData()
  for (const [k, v] of Object.entries(fields ?? {})) if (v) fd.append(k, v)
  fd.append('file', file)
  const res = await raw(path, { method: 'POST', body: fd })
  return res.json()
}

export async function uploadFile(file: File): Promise<FileRef> {
  const fd = new FormData()
  fd.append('file', file)
  const res = await raw('/files', { method: 'POST', body: fd })
  return res.json()
}

// ---- Tiplər ----
export type Money = string
export interface FileRef { id: number; name: string; size: number }

export interface Settings {
  companyName: string; voen: string; address: string; director: string; bank: string; iban: string
  bankCode: string; bankVoen: string; swift: string; correspondentAccount: string; phone: string; email: string; profitTaxRate: string; taxMethod: string
}
export interface PayrollRate {
  id: number; validFrom: string
  dsmfLimit: string; dsmfEmpLow: string; dsmfEmpHigh: string; dsmfErLow: string; dsmfErHigh: string
  unempEmp: string; unempEr: string; medLimit: string; medLow: string; medHigh: string
  incomeLimit: string; incomeExempt: string; incomeLow: string; incomeHigh: string
}
export interface CalendarMonth { month: string; days: number }
export interface Template { id: number; code: string; title: string; format: 'HTML' | 'DOCX'; fileId: number | null; html: string | null; isDefault: boolean }
export interface DealDocument { id: number; dealId: number; code: string; templateId: number | null; title: string; html: string | null; format: 'HTML' | 'DOCX' | 'PDF'; fileId: number | null; hasPrevious: boolean; createdAt: string; updatedAt: string }

export type AccountType = 'BANK' | 'DIRECTOR_CARD' | 'CASH'
export interface Requisites { bankName: string | null; iban: string | null; cardNumber: string | null; note: string | null }
export interface Account extends Requisites { id: number; name: string; type: AccountType; isDefault: boolean; openingBalance: Money; openingDate: string; balance: Money; bankCode: string | null; bankVoen: string | null; swift: string | null; correspondentAccount: string | null }
export type Direction = 'IN' | 'OUT'
export interface Movement {
  id: number; date: string; accountId: number; accountName: string; direction: Direction; amount: Money
  purpose: string; note: string | null; fileId: number | null; dealId: number | null; expenseId: number | null
  allocated: Money; allocations: MovementAlloc[]; statementId: number | null
}
export interface Statement { id: number; accountId: number; periodFrom: string; periodTo: string; fileId: number; fileName: string; fileSize: number; note: string | null; createdAt: string; movementCount: number }
export type PaymentKind = 'ADVANCE' | 'FINAL' | 'OTHER'
export interface MovementAlloc { id: number; movementId: number; dealId: number; contractNo: string | null; customerName: string | null; amount: Money; kind: PaymentKind; fileId: number | null; note: string | null }
export interface Unallocated { id: number; date: string; accountName: string; amount: Money; allocated: Money; remaining: Money; note: string | null }
export interface PaymentSuggestion { movementId: number; date: string; accountName: string; amount: Money; remaining: Money; note: string | null; score: number; reasons: string[] }
export type PaymentTerms = 'PREPAID' | 'PARTIAL' | 'POSTPAID' | 'RETAIL'
export type PaymentStatus = 'UNPAID' | 'PARTIAL' | 'PAID' | 'OVERPAID'
export interface Step { key: string; label: string; optional: boolean; done: boolean; current: boolean }
export interface PaymentItem { id: number; movementId: number; date: string; accountName: string; movementAmount: Money; amount: Money; kind: PaymentKind; fileId: number | null; note: string | null; movementNote: string | null }
export interface DealPayments { price: Money; paid: Money; remaining: Money; status: PaymentStatus; advanceRequired: Money; advancePaid: Money; items: PaymentItem[] }
export interface Expense {
  id: number; date: string; vendor: string; category: string; description: string; amount: Money; currency: string
  rate: Money; amountAzn: Money; accountId: number | null; deductible: boolean; fileId: number | null; note: string | null; itemId: number | null; docDate: string; statementId: number | null
}
export interface ExpenseItem {
  id: number; name: string; vendor: string | null; category: string | null; currency: string; defaultAmount: Money | null
  accountId: number | null; accountName: string | null; deductible: boolean; recurrence: 'MONTHLY' | 'NONE'; active: boolean; note: string | null
}
export interface MissingItem { itemId: number; name: string; vendor: string | null; defaultAmount: Money | null; currency: string; lastAmountAzn: Money | null; lastDate: string | null }
export interface Employee extends Requisites { id: number; name: string; position: string; workplace: 'MAIN' | 'SECONDARY'; gross: Money; active: boolean; fin: string | null; cardExpiry: string | null }
export interface RunSummary { id: number; month: string; status: 'DRAFT' | 'FINAL'; totalGross: Money; totalNet: Money; totalEmployerCost: Money }
export interface PayrollAmounts {
  gross: Money; accrued: Money; income: Money; dsmfEmp: Money; medEmp: Money; unempEmp: Money; net: Money
  dsmfEr: Money; medEr: Money; unempEr: Money; employerCost: Money
}
export interface PayrollLine extends PayrollAmounts {
  id: number; employeeId: number; name: string; position: string; workplace: 'MAIN' | 'SECONDARY'; normDays: number; workedDays: number
}
export interface PayrollRun {
  id: number; month: string; status: 'DRAFT' | 'FINAL'; normDays: number; lines: PayrollLine[]; totals: PayrollAmounts
}
export interface PaymentCode { id: number; sortOrder: number; key: string; title: string; budgetCode: string | null; active: boolean }
export type PayLineStatus = 'PAID' | 'PARTIAL' | 'UNPAID'
export interface PayLink { id: number; movementId: number; date: string | null; amount: Money; note: string | null; fileId: number | null }
export interface Payment { order: number; key: string; title: string; budgetCode: string | null; amount: Money; purpose: string; status: PayLineStatus; paidAmount: Money; fileId: number | null; links: PayLink[] }
export interface EmployeePayment { employeeId: number | null; name: string; bankName: string | null; iban: string | null; cardNumber: string | null; cardExpiry: string | null; amount: Money; purpose: string; status: PayLineStatus; paidAmount: Money; fileId: number | null; links: PayLink[] }
export interface MovementSuggestion { movementId: number; date: string; accountName: string; amount: Money; remaining: Money; purpose: string; note: string | null; score: number; reasons: string[] }
export interface RunFile { id: number; fileId: number; title: string; name: string; size: number; createdAt: string }
export interface UnpaidRun { runId: number; month: string; lines: { key: string; title: string; amount: Money; paidAmount: Money; status: PayLineStatus }[] }
export interface Payments { taxes: Payment[]; employees: EmployeePayment[] }
export interface Counterparty {
  id: number; name: string; voen: string | null; kind: 'CUSTOMER' | 'SUPPLIER' | 'BOTH'; entityType: 'LEGAL' | 'INDIVIDUAL'; address: string | null; director: string | null
  bank: string | null; iban: string | null; bankCode: string | null; bankVoen: string | null; swift: string | null
  correspondentAccount: string | null; phone: string | null; email: string | null; note: string | null; fin: string | null; cardNumber: string | null
}
export type Customer = Counterparty
export type Stage = 'NEW' | 'CONTRACT' | 'PROTOCOL' | 'ADVANCE_INVOICE' | 'PAID' | 'IN_PROGRESS' | 'ACT' | 'INVOICE' | 'DONE'
export interface Product { id: number; name: string; code: string | null; unit: string; defaultPrice: Money | null; defaultComputers: number | null; description: string | null; active: boolean; defaultPaymentTerms: PaymentTerms | null; defaultAdvancePercent: Money | null }
export interface Deal {
  id: number; customerId: number; customerName: string; productId: number | null; productCode: string | null; product: string; description: string | null; price: Money
  computers: number; stage: Stage; contractNo: string | null; contractDate: string | null; protocolDate: string | null
  advanceInvoiceNo: string | null; advanceInvoiceDate: string | null; advanceAmount: Money | null; advanceFileId: number | null
  actNo: string | null; actDate: string | null; actFileId: number | null
  invoiceNo: string | null; invoiceDate: string | null; invoiceAmount: Money | null; invoiceFileId: number | null
  note: string | null; createdAt: string; saleDate: string
  paymentTerms: PaymentTerms; advancePercent: Money; advanceRequired: Money; advancePaid: Money; paid: Money; remaining: Money
  paymentStatus: PaymentStatus; awaitingPayment: boolean; stepper: Step[]; stageSince: string
  cancelled: boolean; cancelReason: string | null; cancelledAt: string | null; hasAllocations: boolean; quantity: number
}
export interface DealEvent { id?: number; createdAt?: string; date?: string; fromStage: Stage | null; toStage: Stage; note: string | null }
export interface Dashboard {
  quarter: { year: number; q: number; income: Money; expenses: Money; profit: Money; profitTax: Money }
  accounts: { id: number; name: string; balance: Money }[]
  ownerDebt: Money
  dealsByStage: Partial<Record<Stage, number>>
  expiringCards: { employeeId: number; name: string; cardExpiry: string; daysLeft: number }[]
  method: string; warnings: string[]
  unpaidPayroll: UnpaidRun[]
  awaitingPayments: { dealId: number; customerName: string; contractNo: string | null; invoiceDate: string | null; price: Money; paid: Money; remaining: Money }[]
}
export interface PayrollMonth {
  month: string; gross: Money; income: Money; dsmfEmp: Money; dsmfEr: Money; medEmp: Money; medEr: Money
  unempEmp: Money; unempEr: Money; net: Money
}
export interface Report {
  period: { from: string; to: string }
  income: Money
  incomeItems: { date: string; invoiceNo: string | null; customer: string; amount: Money; kind: 'INVOICE' | 'RETAIL' }[]
  expenses: Money
  expenseByCategory: { category: string; amount: Money }[]
  incomeByProduct: { product: string; amount: Money }[]
  bankFees: Money; payrollCost: Money; profit: Money; profitTaxRate: Money; profitTax: Money
  payroll: { months: PayrollMonth[]; total: Omit<PayrollMonth, 'month'> & { month?: string } }
  method: string; warnings: string[]
  quarters?: { q: number; income: Money; expenses: Money; profit: Money; profitTax: Money }[]
}

/** Formalarda avtomatik seçilən hesab: əsas hesab, yoxdursa ilk bank hesabı, o da yoxdursa ilk hesab */
export function defaultAccount(accounts: Account[] | undefined): Account | undefined {
  return accounts?.find((a) => a.isDefault) ?? accounts?.find((a) => a.type === 'BANK') ?? accounts?.[0]
}

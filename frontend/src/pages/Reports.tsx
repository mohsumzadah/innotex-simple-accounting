import { useState } from 'react'
import { ActionButton, Badge, Warnings, Empty, ErrorBox, Loading, PageHead } from '../components/ui'
import { download, get, qs, type Report } from '../lib/api'
import { currentQuarter, fmtDate, money, monthName, num } from '../lib/format'
import { useAsync } from '../lib/useAsync'

function Big({ title, value, tone, hint }: { title: string; value: string; tone?: 'accent' | 'ok' | 'bad' | 'warn'; hint?: string }) {
  return (
    <div className={`big ${tone ?? ''}`}>
      <div className="t">{title}</div>
      <div className="v">{value}</div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  )
}

export function Reports() {
  const cq = currentQuarter()
  const [year, setYear] = useState(cq.year)
  const [q, setQ] = useState<number>(cq.q) // 0 = bütün il
  const yearly = q === 0
  const rep = useAsync(() => get<Report>(yearly ? `/reports/year${qs({ year })}` : `/reports/quarter${qs({ year, q })}`), [year, q])
  const r = rep.data
  const xlsx = yearly ? `/reports/year.xlsx${qs({ year })}` : `/reports/quarter.xlsx${qs({ year, q })}`

  return (
    <>
      <PageHead title="Hesabatlar" sub="e-taxes-a köçürmək üçün hazır rəqəmlər. Proqram bəyannaməni özü göndərmir.">
        <ActionButton onRun={() => download(xlsx, yearly ? `hesabat-${year}.xlsx` : `hesabat-${year}-Q${q}.xlsx`)}>Excel yüklə</ActionButton>
      </PageHead>
      <div className="filters">
        <input type="number" min="2020" max="2100" value={year} style={{ width: 90 }} onChange={(e) => setYear(Number(e.target.value) || cq.year)} />
        <div className="tabs" style={{ marginBottom: 0 }}>
          {[1, 2, 3, 4].map((n) => <button key={n} className={q === n ? 'on' : ''} onClick={() => setQ(n)}>{n}-ci rüb</button>)}
          <button className={yearly ? 'on' : ''} onClick={() => setQ(0)}>Bütün il</button>
        </div>
      </div>
      <ErrorBox error={rep.error} />
      {rep.loading && !r ? <Loading /> : r && (
        <>
          <p className="muted">Dövr: {fmtDate(r.period.from)} – {fmtDate(r.period.to)}. <b>Vergi uçotu metodu: Hesablama metodu</b></p>
          <Warnings items={r.warnings} />
          <div className="banner">e-taxes-a köçürmək üçün: aşağıdakı rəqəmləri bəyannamədə uyğun xanalara daxil edin.</div>

          {yearly && r.quarters && (
            <div className="card table-wrap">
              <h3>Rüblər üzrə</h3>
              <table>
                <thead><tr><th>Rüb</th><th className="n">Gəlir</th><th className="n">Xərclər</th><th className="n">Mənfəət</th><th className="n">Mənfəət vergisi</th></tr></thead>
                <tbody>
                  {r.quarters.map((x) => (
                    <tr key={x.q}><td>{x.q}-ci rüb</td><td className="n">{money(x.income)}</td><td className="n">{money(x.expenses)}</td><td className="n">{money(x.profit)}</td><td className="n">{money(x.profitTax)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <Big title="Gəlir" value={money(r.income)} tone="ok" hint="Yekun qaimə (INVOICE) tarixi bu dövrə düşən satışlar; fiziki şəxs satışları tamamlanma (DONE) tarixi ilə. Avans gəlir sayılmır." />
          {r.incomeItems.length > 0 && (
            <div className="card table-wrap">
              <table>
                <thead><tr><th>Tarix</th><th>Qaimə №</th><th>Kontragent</th><th className="n">Məbləğ</th></tr></thead>
                <tbody>
                  {r.incomeItems.map((i, k) => <tr key={k}><td>{fmtDate(i.date)}</td><td>{i.kind === 'RETAIL' ? '—' : i.invoiceNo}</td><td>{i.customer}{i.kind === 'RETAIL' && <> <Badge tone="warn">fiziki şəxs</Badge></>}</td><td className="n">{money(i.amount)}</td></tr>)}
                </tbody>
              </table>
            </div>
          )}

          {r.incomeByProduct.length > 0 && (
            <div className="card table-wrap">
              <h3>Gəlir məhsul üzrə</h3>
              <table>
                <thead><tr><th>Məhsul</th><th className="n">Məbləğ</th></tr></thead>
                <tbody>
                  {r.incomeByProduct.map((x) => <tr key={x.product}><td>{x.product}</td><td className="n">{money(x.amount)}</td></tr>)}
                </tbody>
                <tfoot><tr><th>Cəmi</th><th className="n">{money(r.income)}</th></tr></tfoot>
              </table>
            </div>
          )}

          <Big title="Xərclər" value={money(r.expenses)} hint="Vergidən çıxılan xərclər (sənəd tarixi ilə) + bank komissiyaları + yekunlaşmış maaşların şirkətə xərci" />
          <div className="card table-wrap">
            <table>
              <thead><tr><th>Kateqoriya</th><th className="n">Məbləğ</th></tr></thead>
              <tbody>
                {r.expenseByCategory.map((c) => <tr key={c.category}><td>{c.category}</td><td className="n">{money(c.amount)}</td></tr>)}
                <tr><td>Bank komissiyaları</td><td className="n">{money(r.bankFees)}</td></tr>
                <tr><td>Əmək haqqı xərci (işəgötürən)</td><td className="n">{money(r.payrollCost)}</td></tr>
              </tbody>
              <tfoot><tr><th>Cəmi</th><th className="n">{money(r.expenses)}</th></tr></tfoot>
            </table>
          </div>

          <div className="two">
            <Big title="Mənfəət" value={money(r.profit)} tone={num(r.profit) < 0 ? 'bad' : 'accent'} hint="Gəlir − Xərclər" />
            <Big title={`Mənfəət vergisi (${num(r.profitTaxRate)}%)`} value={money(r.profitTax)} tone="warn" hint={num(r.profit) <= 0 ? 'Zərər olduğu üçün vergi 0' : 'Mənfəət × vergi dərəcəsi'} />
          </div>

          <div className="card table-wrap">
            <h3>Əmək haqqı vergiləri və ödənişləri</h3>
            {!r.payroll.months.length ? <Empty>Bu dövrdə yekunlaşmış maaş cədvəli yoxdur.</Empty> : (
              <table>
                <thead>
                  <tr>
                    <th>Ay</th><th className="n">Gross</th><th className="n">Gəlir vergisi</th><th className="n">DSMF işçi</th><th className="n">DSMF şirkət</th>
                    <th className="n">İTS işçi</th><th className="n">İTS şirkət</th><th className="n">İşsizlik işçi</th><th className="n">İşsizlik şirkət</th><th className="n">NET</th>
                  </tr>
                </thead>
                <tbody>
                  {r.payroll.months.map((m) => (
                    <tr key={m.month}>
                      <td>{monthName(m.month)}</td><td className="n">{money(m.gross)}</td><td className="n">{money(m.income)}</td><td className="n">{money(m.dsmfEmp)}</td>
                      <td className="n">{money(m.dsmfEr)}</td><td className="n">{money(m.medEmp)}</td><td className="n">{money(m.medEr)}</td>
                      <td className="n">{money(m.unempEmp)}</td><td className="n">{money(m.unempEr)}</td><td className="n">{money(m.net)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <th>Cəmi</th><th className="n">{money(r.payroll.total.gross)}</th><th className="n">{money(r.payroll.total.income)}</th><th className="n">{money(r.payroll.total.dsmfEmp)}</th>
                    <th className="n">{money(r.payroll.total.dsmfEr)}</th><th className="n">{money(r.payroll.total.medEmp)}</th><th className="n">{money(r.payroll.total.medEr)}</th>
                    <th className="n">{money(r.payroll.total.unempEmp)}</th><th className="n">{money(r.payroll.total.unempEr)}</th><th className="n">{money(r.payroll.total.net)}</th>
                  </tr>
                </tfoot>
              </table>
            )}
          </div>
        </>
      )}
    </>
  )
}

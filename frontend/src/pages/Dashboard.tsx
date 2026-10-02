import { Link } from 'react-router-dom'
import { Badge, Empty, ErrorBox, Loading, PageHead, Stat, Warnings } from '../components/ui'
import { get, type Dashboard as Dash } from '../lib/api'
import { fmtDate, fmtMonth, money, monthName, num, STAGE, STAGES } from '../lib/format'
import { LineStatusBadge } from './Payroll'
import { useAsync } from '../lib/useAsync'

export function Dashboard() {
  const { data, error, loading } = useAsync(() => get<Dash>('/dashboard'), [])
  if (loading && !data) return <Loading />
  if (!data) return <ErrorBox error={error} />
  const q = data.quarter
  return (
    <>
      <PageHead title="İdarə paneli" sub={`Cari rüb: ${q.year}, ${q.q}-ci rüb. Vergi uçotu metodu: Hesablama metodu`} />
      <Warnings items={data.warnings} />
      <div className="grid">
        <Stat k="Gəlir" v={money(q.income)} tone="ok" />
        <Stat k="Xərclər" v={money(q.expenses)} />
        <Stat k="Mənfəət" v={money(q.profit)} tone={num(q.profit) < 0 ? 'bad' : undefined} />
        <Stat k="Mənfəət vergisi" v={money(q.profitTax)} tone="warn" />
      </div>
      {data.expiringCards.length > 0 && (
        <div className="card">
          <h3>Bank kartı bitən işçilər</h3>
          <table>
            <tbody>
              {data.expiringCards.map((c) => (
                <tr key={c.employeeId}>
                  <td><Link to="/payroll">{c.name}</Link></td>
                  <td>{fmtMonth(c.cardExpiry)}</td>
                  <td className="n">{c.daysLeft < 0 ? <Badge tone="bad">bitib</Badge> : <Badge tone="warn">{c.daysLeft} gün qalıb</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data.unpaidPayroll.length > 0 && (
        <div className="card" style={{ borderColor: 'var(--warn)' }}>
          <div className="head">
            <h3>Ödənilməmiş maaş / vergi</h3>
          </div>
          {data.unpaidPayroll.map((r) => (
            <div key={r.runId} style={{ marginBottom: 10 }}>
              <b><Link to={`/payroll/${r.runId}`}>{monthName(r.month)}</Link></b>
              <div className="table-wrap">
                <table>
                  <tbody>
                    {r.lines.map((l) => (
                      <tr key={l.key}>
                        <td>{l.title}</td>
                        <td className="n">{money(l.amount)}</td>
                        <td className="n"><LineStatusBadge s={l.status} />{l.status === 'PARTIAL' && <span className="muted"> {money(l.paidAmount)}</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      )}
      {data.awaitingPayments.length > 0 && (
        <div className="card">
          <div className="head">
            <h3>Ödəniş gözlənilən satışlar</h3>
            <Link to="/deals?awaiting=1">Hamısı</Link>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Müştəri</th><th>Müqavilə №</th><th>Qaimə tarixi</th><th className="n">Qiymət</th><th className="n">Ödənilib</th><th className="n">Qalıq</th></tr></thead>
              <tbody>
                {data.awaitingPayments.map((a) => (
                  <tr key={a.dealId}>
                    <td><Link to={`/deals/${a.dealId}`}>{a.customerName}</Link></td>
                    <td>{a.contractNo ?? '—'}</td>
                    <td>{fmtDate(a.invoiceDate)}</td>
                    <td className="n">{money(a.price)}</td>
                    <td className="n">{money(a.paid)}</td>
                    <td className="n"><b>{money(a.remaining)}</b></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      <div className="two">
        <div className="card">
          <h3>Hesab qalıqları</h3>
          {data.accounts.length === 0 ? (
            <Empty>Hələ hesab yoxdur.</Empty>
          ) : (
            <table>
              <tbody>
                {data.accounts.map((a) => (
                  <tr key={a.id}>
                    <td>{a.name}</td>
                    <td className="n">{money(a.balance)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="explain">
            Şirkətin direktora borcu: <b>{money(data.ownerDebt)}</b>
            <div className="muted">Şəxsi kartdan ödənmiş xərclər minus direktora qaytarılan məbləğlər.</div>
          </div>
        </div>
        <div className="card">
          <h3>Açıq satışlar</h3>
          <table>
            <tbody>
              {STAGES.filter((s) => s !== 'DONE').map((s) => (
                <tr key={s}>
                  <td><Link to={`/deals?stage=${s}`}>{STAGE[s]}</Link></td>
                  <td className="n">{data.dealsByStage[s] ?? 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}

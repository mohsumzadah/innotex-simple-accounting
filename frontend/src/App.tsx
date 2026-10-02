import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { Loading } from './components/ui'
import { useSession } from './lib/session'
import { Accounts } from './pages/Accounts'
import { Customers } from './pages/Customers'
import { Dashboard } from './pages/Dashboard'
import { DealView } from './pages/DealView'
import { Deals } from './pages/Deals'
import { ExpenseItems } from './pages/ExpenseItems'
import { Expenses } from './pages/Expenses'
import { Login } from './pages/Login'
import { Products } from './pages/Products'
import { Payroll, PayrollRunView } from './pages/Payroll'
import { Reports } from './pages/Reports'
import { Settings } from './pages/Settings'

export default function App() {
  const { email, ready } = useSession()
  if (!ready) return <Loading />
  if (!email) return <Login />
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="deals" element={<Deals />} />
        <Route path="deals/:id" element={<DealView />} />
        <Route path="customers" element={<Customers />} />
        <Route path="expenses" element={<Expenses />} />
        <Route path="expense-items" element={<ExpenseItems />} />
        <Route path="products" element={<Products />} />
        <Route path="accounts" element={<Accounts />} />
        <Route path="payroll" element={<Payroll />} />
        <Route path="payroll/:id" element={<PayrollRunView />} />
        <Route path="reports" element={<Reports />} />
        <Route path="settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

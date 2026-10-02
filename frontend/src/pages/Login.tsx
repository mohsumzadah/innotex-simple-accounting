import { useState, type FormEvent } from 'react'
import { ErrorBox } from '../components/ui'
import { useSession } from '../lib/session'

export function Login() {
  const { login } = useSession()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await login(email.trim(), password)
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <div className="center">
      <form className="card login" onSubmit={submit} style={{ padding: 22 }}>
        <div className="logo"><img src={document.documentElement.dataset.theme === 'dark' ? '/brand/logo-dark.png' : '/brand/logo.png'} alt="innoTek" style={{ height: 32, width: 'auto', display: 'block', margin: '0 auto 6px' }} /><span className="muted">Sadə Uçot</span></div>
        <div className="sub">Hesabınıza daxil olun</div>
        <ErrorBox error={error} />
        <div className="form" style={{ gridTemplateColumns: '1fr' }}>
          <label className="f">
            <span>E-poçt</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus required />
          </label>
          <label className="f">
            <span>Parol</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          <button className="btn" disabled={busy}>{busy ? 'Gözləyin…' : 'Daxil ol'}</button>
        </div>
      </form>
    </div>
  )
}

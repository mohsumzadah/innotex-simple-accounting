import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { get, getToken, post, setToken, setUnauthorizedHandler, type Me } from './api'

interface Session {
  me: Me | null
  email: string | null
  isAdmin: boolean
  ready: boolean
  login: (e: string, p: string) => Promise<void>
  logout: () => void
}
const Ctx = createContext<Session>(null!)
// eslint-disable-next-line react-refresh/only-export-components
export const useSession = () => useContext(Ctx)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(!getToken())

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null))
    if (getToken()) {
      get<Me>('/auth/me')
        .then(setMe)
        .catch(() => setToken(null))
        .finally(() => setReady(true))
    }
  }, [])

  const login = async (e: string, p: string) => {
    const r = await post<{ token: string }>('/auth/login', { email: e, password: p })
    setToken(r.token)
    setMe(await get<Me>('/auth/me'))
  }
  const logout = () => {
    post('/auth/logout').catch(() => {})
    setToken(null)
    setMe(null)
  }
  return (
    <Ctx.Provider value={{ me, email: me?.email ?? null, isAdmin: me?.role === 'ADMIN', ready, login, logout }}>
      {children}
    </Ctx.Provider>
  )
}

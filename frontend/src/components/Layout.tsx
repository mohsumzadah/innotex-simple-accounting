import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import {
  BarChart3, Check, Contact, LayoutDashboard, LogOut, Menu, Palette, PanelLeftClose, PanelLeftOpen,
  Package, Receipt, Settings as SettingsIcon, ShoppingCart, Tags, Users, Wallet, X, type LucideIcon,
} from 'lucide-react'
import { useSession } from '../lib/session'
import { ROLE_LABEL } from '../lib/api'
import { THEMES, useTheme, type ThemeKey } from '../lib/theme'

interface Item { to: string; text: string; icon: LucideIcon }
const GROUPS: { title?: string; items: Item[] }[] = [
  { items: [{ to: '/', text: 'İdarə paneli', icon: LayoutDashboard }] },
  { title: 'Satış', items: [
    { to: '/deals', text: 'Satışlar', icon: ShoppingCart },
    { to: '/customers', text: 'Kontragentlər', icon: Contact },
    { to: '/products', text: 'Nomenklatura', icon: Package },
  ] },
  { title: 'Maliyyə', items: [
    { to: '/accounts', text: 'Hesablar', icon: Wallet },
    { to: '/expenses', text: 'Xərclər', icon: Receipt },
    { to: '/expense-items', text: 'Xərc maddələri', icon: Tags },
  ] },
  { title: 'Kadr', items: [{ to: '/payroll', text: 'Əmək haqqı', icon: Users }] },
  { title: 'Hesabat', items: [{ to: '/reports', text: 'Hesabatlar', icon: BarChart3 }] },
]
const SETTINGS: Item = { to: '/settings', text: 'Ayarlar', icon: SettingsIcon }
const COLLAPSE_KEY = 'sade.sidebar.collapsed'

function readCollapsed() {
  try { return localStorage.getItem(COLLAPSE_KEY) === '1' } catch { return false }
}

/** INNOTEX loqosu (02_Brendinq və Media/Loqolar): tünd mövzuda "Tek." hissəsi açıq rəngli variantdadır */
const Logo = ({ dark }: { dark: boolean }) => <img className="logo-full" src={dark ? "/brand/logo-dark.png" : "/brand/logo.png"} alt="innoTek" />
const LogoIcon = ({ dark }: { dark: boolean }) => <img className="logo-icon" src={dark ? "/brand/icon-dark.png" : "/brand/icon.png"} alt="innoTek" />

function ThemePicker({ theme, setTheme }: { theme: ThemeKey; setTheme: (t: ThemeKey) => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const off = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', off)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', off); document.removeEventListener('keydown', esc) }
  }, [open])
  return (
    <div className="tp" ref={ref}>
      <button className="tp-btn" onClick={() => setOpen((v) => !v)} title="Tema seç" aria-label="Tema seç" aria-expanded={open}>
        <Palette size={15} />
      </button>
      {open && (
        <div className="tp-pop" role="menu">
          {THEMES.map((t) => (
            <button key={t.key} role="menuitemradio" aria-checked={theme === t.key} onClick={() => { setTheme(t.key); setOpen(false) }}>
              <span className="dot-c" style={{ background: t.swatch ?? t.accent, border: `1px solid ${t.border}` }} />
              <span className="lbl2">{t.label}</span>
              {theme === t.key && <Check size={13} />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export function Layout() {
  const { me, logout } = useSession()
  const { theme, setTheme } = useTheme()
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const [open, setOpen] = useState(false)

  function toggleCollapsed() {
    const next = !collapsed
    try { localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0') } catch { /* yaddaş əlçatmazdır */ }
    setCollapsed(next)
  }

  const link = (l: Item) => (
    <NavLink key={l.to} to={l.to} end={l.to === '/'} title={collapsed ? l.text : undefined} data-tip={l.text} onClick={() => setOpen(false)}>
      <l.icon size={16} strokeWidth={1.9} aria-hidden />
      <span className="lbl">{l.text}</span>
    </NavLink>
  )

  return (
    <div className={`shell${collapsed ? ' collapsed' : ''}${open ? ' drawer-open' : ''}`}>
      <div className="backdrop" onClick={() => setOpen(false)} />
      <nav className="nav" aria-label="Əsas menyu">
        <div className="brand">
          <LogoIcon dark={theme === 'dark'} />
          <div className="brand-text"><Logo dark={theme === 'dark'} /><span>Sadə Uçot</span></div>
          <button className="icon-btn drawer-close" onClick={() => setOpen(false)} aria-label="Menyunu bağla"><X size={18} /></button>
        </div>
        <div className="nav-scroll">
          {GROUPS.map((g, i) => (
            <div className="sec" key={i}>
              {g.title ? <div className="group"><span>{g.title}</span></div> : null}
              {g.items.map(link)}
            </div>
          ))}
        </div>
        <div className="nav-bottom">
          {link(SETTINGS)}
          <div className="user">
            <span className="email" title={me ? `${me.email} · ${ROLE_LABEL[me.role]}` : ''}>{me?.name || me?.email}</span>
            <button className="nav-btn logout" onClick={logout} title="Çıxış" data-tip="Çıxış">
              <LogOut size={16} /><span className="lbl">Çıxış</span>
            </button>
          </div>
          <button className="nav-btn collapse-btn" onClick={toggleCollapsed} title={collapsed ? 'Menyunu genişləndir' : 'Menyunu yığ'} data-tip={collapsed ? 'Genişləndir' : 'Yığ'}>
            {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
            <span className="lbl">Menyunu yığ</span>
          </button>
        </div>
      </nav>
      <div className="col">
        <header className="topbar no-print">
          <button className="icon-btn mob-only" onClick={() => setOpen(true)} aria-label="Menyunu aç"><Menu size={20} /></button>
          <span className="mob-only brand-m"><Logo dark={theme === 'dark'} /><span className="muted">Sadə Uçot</span></span>
          <span className="grow" />
          <ThemePicker theme={theme} setTheme={setTheme} />
        </header>
        <main className="main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

import { useCallback, useEffect, useState } from 'react'

// 4 rəng teması (Linko ilə eyni paletlər; dəyişənlər styles.css-də [data-theme] ilə verilib).
export type ThemeKey = 'light' | 'dark' | 'pink' | 'blue'
export const THEMES: { key: ThemeKey; label: string; accent: string; border: string; swatch?: string }[] = [
  { key: 'light', label: 'Açıq', accent: '#BD5F0C', border: '#E2E6EC' },
  { key: 'dark', label: 'Tünd', accent: '#E8A23D', border: '#2B3542', swatch: '#000000' },
  { key: 'pink', label: 'Çəhrayı', accent: '#D6336C', border: '#F6D2E1' },
  { key: 'blue', label: 'Göy', accent: '#2159D6', border: '#CCDFF2' },
]
const KEY = 'sade.theme'
const DEFAULT: ThemeKey = 'light'

// Köhnə dəyərlər: light/dark eyni qalır, "system" susmaya görə temaya keçir.
function readTheme(): ThemeKey {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'light' || v === 'dark' || v === 'pink' || v === 'blue') return v
  } catch { /* yaddaş əlçatmazdır */ }
  return DEFAULT
}

export function useTheme() {
  const [theme, setThemeState] = useState<ThemeKey>(readTheme)
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
  }, [theme])
  const setTheme = useCallback((t: ThemeKey) => {
    try { localStorage.setItem(KEY, t) } catch { /* yaddaş əlçatmazdır */ }
    setThemeState(t)
  }, [])
  return { theme, setTheme }
}

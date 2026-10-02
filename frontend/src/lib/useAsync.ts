import { useCallback, useEffect, useState } from 'react'

/** deps dəyişəndə və ya reload() çağırılanda fn yenidən işləyir */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<unknown>()
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps)
  useEffect(() => {
    let alive = true
    setLoading(true)
    run()
      .then((d) => {
        if (alive) {
          setData(d)
          setError(undefined)
        }
      })
      .catch((e) => alive && setError(e))
      .finally(() => alive && setLoading(false))
    return () => {
      alive = false
    }
  }, [run, tick])
  return { data, error, loading, reload: () => setTick((t) => t + 1) }
}

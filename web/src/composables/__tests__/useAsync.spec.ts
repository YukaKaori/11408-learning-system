import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/types'
import { useAsync } from '../useAsync'

/** A promise whose settlement the test controls. */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

async function settled() {
  // Two microtask hops: one for the task promise, one for the finally block.
  await Promise.resolve()
  await Promise.resolve()
}

describe('useAsync', () => {
  it('starts loading immediately and exposes the resolved data', async () => {
    const task = deferred<string[]>()
    const { data, loading, error } = useAsync(() => task.promise)

    expect(loading.value).toBe(true)
    expect(data.value).toBeNull()
    expect(error.value).toBeNull()

    task.resolve(['a', 'b'])
    await settled()

    expect(loading.value).toBe(false)
    expect(data.value).toEqual(['a', 'b'])
    expect(error.value).toBeNull()
  })

  it('keeps an ApiError intact for t(error.messageKey) rendering', async () => {
    const failure = new ApiError(180001, 'range invalid', 'error.server')
    const { loading, error } = useAsync(() => Promise.reject(failure))

    await settled()

    expect(loading.value).toBe(false)
    expect(error.value).toBe(failure)
  })

  it('normalizes non-ApiError failures to error.unknown', async () => {
    const { error } = useAsync(() => Promise.reject(new Error('boom')))

    await settled()

    expect(error.value).toBeInstanceOf(ApiError)
    expect(error.value!.messageKey).toBe('error.unknown')
    expect(error.value!.message).toBe('boom')
  })

  it('reload clears the error and fetches again', async () => {
    let attempt = 0
    const { data, error, reload } = useAsync(() =>
      ++attempt === 1
        ? Promise.reject(new ApiError(-1, 'offline', 'error.network'))
        : Promise.resolve('recovered'),
    )

    await settled()
    expect(error.value).not.toBeNull()

    const retry = reload()
    expect(error.value).toBeNull()
    await retry

    expect(data.value).toBe('recovered')
    expect(error.value).toBeNull()
  })

  it('a stale run never overwrites a newer one', async () => {
    const first = deferred<string>()
    const second = deferred<string>()
    const runs = [first, second]
    let started = 0
    const { data, loading, reload } = useAsync(() => runs[started++]!.promise)

    const secondRun = reload()
    second.resolve('newer')
    await secondRun
    expect(data.value).toBe('newer')
    expect(loading.value).toBe(false)

    first.resolve('stale')
    await settled()
    expect(data.value).toBe('newer')
    expect(loading.value).toBe(false)
  })

  it('a stale failure is discarded too', async () => {
    const first = deferred<string>()
    const second = deferred<string>()
    const runs = [first, second]
    let started = 0
    const { data, error, reload } = useAsync(() => runs[started++]!.promise)

    const secondRun = reload()
    second.resolve('newer')
    await secondRun

    first.reject(new ApiError(-1, 'stale failure', 'error.timeout'))
    await settled()

    expect(data.value).toBe('newer')
    expect(error.value).toBeNull()
  })
})

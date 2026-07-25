import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useAutosave } from '@/composables/useAutosave'

describe('useAutosave', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  function setup(save: (content: string, key: string) => Promise<void>) {
    const key = ref<string | null>('a')
    const content = ref<string>('hello')
    const scope = effectScope()
    let api!: ReturnType<typeof useAutosave<string>>
    scope.run(() => {
      api = useAutosave<string>({
        key: () => key.value,
        source: () => content.value,
        save,
        debounceMs: 1000,
      })
    })
    return { key, content, api }
  }

  it('debounces an edit, then saves once with the current content and key', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { content, api } = setup(save)

    content.value = 'hello world'
    await nextTick()
    expect(save).not.toHaveBeenCalled() // still within the debounce window
    expect(api.isDirty.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)
    expect(save).toHaveBeenCalledTimes(1)
    expect(save).toHaveBeenCalledWith('hello world', 'a')
    expect(api.state.value).toBe('saved')
    expect(api.isDirty.value).toBe(false)
  })

  it('coalesces rapid edits into a single trailing save', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { content } = setup(save)

    content.value = 'a'
    await nextTick()
    await vi.advanceTimersByTimeAsync(400)
    content.value = 'ab'
    await nextTick()
    await vi.advanceTimersByTimeAsync(400)
    content.value = 'abc'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)

    expect(save).toHaveBeenCalledTimes(1)
    expect(save).toHaveBeenCalledWith('abc', 'a')
  })

  it('flushes the previous note immediately when the key changes', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { key, content } = setup(save)

    content.value = 'edited A'
    await nextTick() // dirty, scheduled for note "a"

    key.value = 'b'
    content.value = 'note B body'
    await nextTick() // switching notes flushes "a" right away
    await vi.advanceTimersByTimeAsync(0)

    expect(save).toHaveBeenCalledTimes(1)
    expect(save).toHaveBeenCalledWith('edited A', 'a') // the new load is not an edit
  })

  it('flush() persists a pending edit without waiting for the debounce', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { content, api } = setup(save)

    content.value = 'quick'
    await nextTick()
    await api.flush()

    expect(save).toHaveBeenCalledWith('quick', 'a')
    expect(api.state.value).toBe('saved')
  })

  it('surfaces the error state when a save rejects', async () => {
    const save = vi.fn().mockRejectedValue(new Error('boom'))
    const { content, api } = setup(save)

    content.value = 'change'
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)

    expect(api.state.value).toBe('error')
    expect(api.isDirty.value).toBe(true)
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { login } from '../api/auth'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
}))

describe('auth store', () => {
  beforeEach(() => {
    const values = new Map<string, string>()
    const storage = {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => values.set(key, value)),
      removeItem: vi.fn((key: string) => values.delete(key)),
      clear: vi.fn(() => values.clear()),
    }

    vi.stubGlobal('localStorage', storage)
    Object.defineProperty(window, 'localStorage', { value: storage, configurable: true })
    setActivePinia(createPinia())
  })

  it('tracks login state in local storage', async () => {
    vi.mocked(login).mockResolvedValue({
      username: 'admin',
      tokenType: 'Bearer',
      accessToken: 'development-placeholder-token',
    })
    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(false)
    await auth.login('admin', 'admin123')

    expect(auth.isAuthenticated).toBe(true)
    expect(login).toHaveBeenCalledWith('admin', 'admin123')
    expect(window.localStorage.getItem('wzut-wallpaper-token')).toBe('development-placeholder-token')
  })
})

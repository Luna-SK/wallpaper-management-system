import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { getSessionPolicy, login, register, type LoginResponse, type SessionPolicy } from '../api/auth'

vi.mock('../api/auth', () => ({
  changePassword: vi.fn(),
  getMe: vi.fn(),
  getSessionPolicy: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  refreshSession: vi.fn(),
  updateProfile: vi.fn(),
}))

function sessionPolicy(overrides: Partial<SessionPolicy> = {}): SessionPolicy {
  return {
    idleTimeoutEnabled: true,
    idleTimeoutMinutes: 120,
    absoluteLifetimeEnabled: true,
    absoluteLifetimeDays: 7,
    absoluteExpiresAt: '2026-05-03T00:00:00Z',
    serverTime: '2026-04-26T00:00:00Z',
    ...overrides,
  }
}

function authResponse(overrides: Partial<LoginResponse> = {}): LoginResponse {
  return {
    username: 'admin',
    tokenType: 'Bearer',
    accessToken: 'development-placeholder-token',
    refreshToken: 'refresh-placeholder-token',
    accessTokenExpiresAt: '2026-04-26T00:15:00Z',
    refreshTokenExpiresAt: '2026-05-03T00:00:00Z',
    sessionPolicy: sessionPolicy(),
    permissions: ['image:view', 'user:manage'],
    user: {
      id: 'user-1',
      username: 'admin',
      displayName: '系统管理员',
      email: null,
      phone: null,
      status: 'ACTIVE',
      roles: [{ id: 'role-1', code: 'SYSTEM_ADMIN', name: '系统管理员' }],
      permissions: [
        { id: 'permission-1', code: 'image:view', name: '图片查看', resource: 'image', action: 'view' },
        { id: 'permission-2', code: 'user:manage', name: '用户管理', resource: 'user', action: 'manage' },
      ],
    },
    ...overrides,
  }
}

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
    vi.mocked(getSessionPolicy).mockResolvedValue(sessionPolicy())
    vi.useRealTimers()
  })

  it('tracks login state in local storage', async () => {
    vi.mocked(login).mockResolvedValue(authResponse())
    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(false)
    await auth.login('admin', 'admin123')

    expect(auth.isAuthenticated).toBe(true)
    expect(login).toHaveBeenCalledWith('admin', 'admin123')
    expect(window.localStorage.getItem('wzut-wallpaper-token')).toBe('development-placeholder-token')
    expect(auth.displayName).toBe('系统管理员')
    expect(auth.hasPermission('user:manage')).toBe(true)
    expect(auth.sessionPolicy?.idleTimeoutMinutes).toBe(120)
  })

  it('tracks register state in local storage', async () => {
    vi.mocked(register).mockResolvedValue(authResponse({
      username: 'viewer-one',
      accessToken: 'registered-access-token',
      refreshToken: 'registered-refresh-token',
      permissions: ['image:view'],
      user: {
        id: 'user-2',
        username: 'viewer-one',
        displayName: '浏览用户',
        email: 'viewer@example.com',
        phone: null,
        status: 'ACTIVE',
        roles: [{ id: 'role-2', code: 'VIEWER', name: '浏览者' }],
        permissions: [
          { id: 'permission-1', code: 'image:view', name: '图片查看', resource: 'image', action: 'view' },
        ],
      },
    }))
    const auth = useAuthStore()

    await auth.register({
      username: 'viewer-one',
      password: 'admin123',
      displayName: '浏览用户',
      email: 'viewer@example.com',
      phone: null,
    })

    expect(register).toHaveBeenCalledWith({
      username: 'viewer-one',
      password: 'admin123',
      displayName: '浏览用户',
      email: 'viewer@example.com',
      phone: null,
    })
    expect(auth.isAuthenticated).toBe(true)
    expect(window.localStorage.getItem('wzut-wallpaper-token')).toBe('registered-access-token')
    expect(auth.displayName).toBe('浏览用户')
    expect(auth.hasPermission('image:view')).toBe(true)
    expect(auth.hasPermission('user:manage')).toBe(false)
  })

  it('does not persist a failed register attempt', async () => {
    vi.mocked(register).mockRejectedValue(new Error('用户名已存在'))
    const auth = useAuthStore()

    await expect(auth.register({
      username: 'viewer-one',
      password: 'admin123',
      displayName: '浏览用户',
    })).rejects.toThrow('用户名已存在')

    expect(auth.isAuthenticated).toBe(false)
    expect(window.localStorage.getItem('wzut-wallpaper-token')).toBeNull()
  })

  it('expires an idle session after the configured timeout', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-26T00:00:00Z'))
    const policy = sessionPolicy({
      idleTimeoutEnabled: true,
      idleTimeoutMinutes: 15,
      absoluteLifetimeEnabled: false,
      absoluteExpiresAt: null,
    })
    vi.mocked(login).mockResolvedValue(authResponse({ sessionPolicy: policy }))
    vi.mocked(getSessionPolicy).mockResolvedValue(policy)
    const auth = useAuthStore()
    const expired = vi.fn()

    await auth.login('admin', 'admin123')
    auth.startSessionLifecycleMonitor(expired)
    vi.advanceTimersByTime(15 * 60_000)

    expect(auth.isAuthenticated).toBe(false)
    expect(expired).toHaveBeenCalledWith('登录空闲超时，请重新登录')
    auth.stopSessionLifecycleMonitor()
  })

  it('uses the default idle policy before the server policy is loaded', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-26T00:00:00Z'))
    vi.mocked(getSessionPolicy).mockReturnValue(new Promise<SessionPolicy>(() => undefined))
    window.localStorage.setItem('wzut-wallpaper-auth', JSON.stringify({
      accessToken: 'stored-access-token',
      refreshToken: 'stored-refresh-token',
      user: authResponse().user,
      permissions: ['image:view'],
      lastActivityAt: Date.now(),
    }))
    const auth = useAuthStore()
    const expired = vi.fn()

    auth.startSessionLifecycleMonitor(expired)
    vi.advanceTimersByTime(120 * 60_000)

    expect(auth.isAuthenticated).toBe(false)
    expect(expired).toHaveBeenCalledWith('登录空闲超时，请重新登录')
    auth.stopSessionLifecycleMonitor()
  })

  it('extends idle timeout after user activity', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-26T00:00:00Z'))
    const policy = sessionPolicy({
      idleTimeoutEnabled: true,
      idleTimeoutMinutes: 15,
      absoluteLifetimeEnabled: false,
      absoluteExpiresAt: null,
    })
    vi.mocked(login).mockResolvedValue(authResponse({ sessionPolicy: policy }))
    vi.mocked(getSessionPolicy).mockResolvedValue(policy)
    const auth = useAuthStore()
    const expired = vi.fn()

    await auth.login('admin', 'admin123')
    auth.startSessionLifecycleMonitor(expired)
    vi.advanceTimersByTime(10 * 60_000)
    auth.recordSessionActivity(false)
    vi.advanceTimersByTime(10 * 60_000)

    expect(auth.isAuthenticated).toBe(true)
    expect(expired).not.toHaveBeenCalled()
    auth.stopSessionLifecycleMonitor()
  })

  it('expires at the absolute session deadline', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-26T00:00:00Z'))
    const policy = sessionPolicy({
      idleTimeoutEnabled: false,
      absoluteLifetimeEnabled: true,
      absoluteExpiresAt: '2026-04-26T01:00:00Z',
    })
    vi.mocked(login).mockResolvedValue(authResponse({ sessionPolicy: policy }))
    vi.mocked(getSessionPolicy).mockResolvedValue(policy)
    const auth = useAuthStore()
    const expired = vi.fn()

    await auth.login('admin', 'admin123')
    auth.startSessionLifecycleMonitor(expired)
    vi.advanceTimersByTime(60 * 60_000)

    expect(auth.isAuthenticated).toBe(false)
    expect(expired).toHaveBeenCalledWith('登录已超过最长会话时长，请重新登录')
    auth.stopSessionLifecycleMonitor()
  })
})

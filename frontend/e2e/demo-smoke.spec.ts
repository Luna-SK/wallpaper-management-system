import { expect, test, type Page, type Route } from '@playwright/test'

const sessionPolicy = {
  idleTimeoutEnabled: true,
  idleTimeoutMinutes: 120,
  absoluteLifetimeEnabled: true,
  absoluteLifetimeDays: 7,
  absoluteExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
  serverTime: new Date().toISOString(),
}

const permissions = [
  {
    id: 'permission-image-view',
    code: 'image:view',
    name: '查看图片',
    resource: 'image',
    action: 'view',
  },
]

const user = {
  id: 'user-admin',
  username: 'admin',
  displayName: '管理员',
  email: null,
  phone: null,
  avatarUrl: null,
  status: 'ACTIVE',
  roles: [{ id: 'role-admin', code: 'SYSTEM_ADMIN', name: '系统管理员' }],
  permissions,
}

function apiResponse<T>(data: T) {
  return {
    code: 'OK',
    message: 'success',
    data,
    traceId: null,
  }
}

async function fulfillJson(route: Route, payload: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(payload),
  })
}

async function mockPublicApi(page: Page) {
  await page.route('**/api/auth/password-reset-policy', (route) => (
    fulfillJson(route, apiResponse({ emailResetEnabled: false }))
  ))
  await page.route('**/api/system/health', (route) => fulfillJson(route, apiResponse({ status: 'UP' })))
}

async function mockAuthenticatedApi(page: Page) {
  await page.route('**/api/auth/login', (route) => (
    fulfillJson(route, apiResponse({
      username: 'admin',
      tokenType: 'Bearer',
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      accessTokenExpiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
      refreshTokenExpiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
      user,
      permissions: ['image:view'],
      sessionPolicy,
    }))
  ))
  await page.route('**/api/auth/session-policy', (route) => fulfillJson(route, sessionPolicy))
  await page.route('**/api/categories', (route) => fulfillJson(route, []))
  await page.route('**/api/tags', (route) => fulfillJson(route, []))
  await page.route(/\/api\/images(?:\?.*)?$/, (route) => fulfillJson(route, {
    items: [],
    page: 1,
    size: 20,
    total: 0,
  }))
}

test('login page opens', async ({ page }) => {
  await mockPublicApi(page)

  await page.goto('/login')

  await expect(page.getByRole('heading', { name: '图片管理系统' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '登录系统' })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录' })).toBeVisible()
  await expect(page.getByText('忘记密码？')).toHaveCount(0)
})

test('health API is reachable through the frontend origin', async ({ page }) => {
  await mockPublicApi(page)
  await page.goto('/login')

  const health = await page.evaluate(async () => {
    const response = await fetch('/api/system/health')
    return response.json()
  })

  expect(health.data.status).toBe('UP')
})

test('login flow reaches image library workspace', async ({ page }) => {
  await mockPublicApi(page)
  await mockAuthenticatedApi(page)

  await page.goto('/login')
  await page.locator('.login-form input').nth(0).fill('admin')
  await page.locator('.login-form input').nth(1).fill('password')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/images$/)
  await expect(page.getByText('图片库').first()).toBeVisible()
  await expect(page.getByPlaceholder('标题')).toBeVisible()
  await expect(page.getByRole('button', { name: '筛选' })).toBeVisible()
})

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import { useCartStore } from './cart'

export interface UserProfile {
  sub: string
  preferred_username: string
  roles: string[]
  email?: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  name?: string
}

// Pre-seeded credentials matching infra/keycloak/realm-export.json
const SEEDED_CREDENTIALS: Record<string, { password: string; roles: string[]; email: string }> = {
  user1: { password: 'password123', roles: ['USER'], email: 'user1@example.com' },
  admin1: { password: 'admin123', roles: ['ADMIN', 'USER'], email: 'admin1@example.com' },
  manager1: { password: 'manager123', roles: ['MANAGER', 'USER'], email: 'manager1@example.com' },
  dev1: { password: 'dev123', roles: ['DEVELOPER', 'USER'], email: 'dev1@example.com' }
}

/**
 * In-Memory Authentication Store (Hybrid Keycloak + Dev Fallback).
 *
 * <p>SECURITY MANDATE: The access token is stored strictly in JavaScript heap memory
 * and NEVER written to localStorage or sessionStorage. This protects session tokens
 * against client-side script exfiltration (XSS).
 */
export const useAuthStore = defineStore('auth', () => {
  // In-memory token storage only
  const accessToken = ref<string | null>(null)
  const user = ref<UserProfile | null>(null)
  const isAuthModalOpen = ref(false)
  const authMode = ref<'keycloak' | 'local'>('local')
  const localRegisteredUsers = ref<Record<string, { password: string; roles: string[]; email: string }>>({})

  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') || user.value?.roles.includes('ROLE_ADMIN') || false)
  const isManager = computed(() => user.value?.roles.includes('MANAGER') || user.value?.roles.includes('ROLE_MANAGER') || false)
  const canManageProducts = computed(() => isAdmin.value || isManager.value)
  const isDeveloper = computed(() => user.value?.roles.includes('DEVELOPER') || user.value?.roles.includes('ROLE_DEVELOPER') || false)
  const currentUserId = computed(() => user.value?.sub || '')
  const token = computed(() => accessToken.value)

  function openAuthModal() {
    isAuthModalOpen.value = true
  }

  function closeAuthModal() {
    isAuthModalOpen.value = false
  }

  function toggleAuthModal() {
    isAuthModalOpen.value = !isAuthModalOpen.value
  }

  function setSession(tokenValue: string, profile: UserProfile, mode: 'keycloak' | 'local' = 'local') {
    accessToken.value = tokenValue
    user.value = profile
    authMode.value = mode

    try {
      const cartStore = useCartStore()
      cartStore.syncCartWithServer()
    } catch {
      // Store initializing or isolated context
    }
  }

  /**
   * Hybrid Login:
   * 1. Resolves username or email interchangeably.
   * 2. Attempts Keycloak OAuth 2.0 Direct Access Grants (Port 8180 or VITE_KEYCLOAK_URL).
   * 3. If Keycloak is unreachable or offline, falls back seamlessly to pre-seeded local accounts.
   */
  async function login(identifier: string, password: string): Promise<UserProfile> {
    const rawIdentifier = (identifier || '').trim()
    const normalizedInput = rawIdentifier.toLowerCase()
    const allAccounts = { ...SEEDED_CREDENTIALS, ...localRegisteredUsers.value }

    // Resolve account by username OR email (case-insensitive)
    const matchedEntry = Object.entries(allAccounts).find(
      ([uname, acc]) => uname.toLowerCase() === normalizedInput || (acc.email && acc.email.toLowerCase() === normalizedInput)
    )

    const resolvedUsername = matchedEntry ? matchedEntry[0] : rawIdentifier
    const resolvedAccount = matchedEntry ? matchedEntry[1] : null

    const keycloakBase = (import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180').replace(/\/+$/, '')
    const keycloakTokenUrl = `${keycloakBase}/realms/shopping-cart/protocol/openid-connect/token`

    try {
      const params = new URLSearchParams()
      params.append('client_id', 'shopping-cart-spa')
      params.append('grant_type', 'password')
      params.append('username', resolvedUsername)
      params.append('password', password)
      params.append('scope', 'openid')

      const response = await axios.post(keycloakTokenUrl, params, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        timeout: 2500
      })

      const rawToken = response.data.access_token
      let roles: string[] = ['USER']
      try {
        const payloadBase64 = rawToken.split('.')[1]
        const decoded = JSON.parse(atob(payloadBase64))
        roles = decoded.realm_access?.roles || ['USER']
      } catch (e) {
        roles = ['USER']
      }

      const userEmail = resolvedAccount?.email || `${resolvedUsername}@example.com`
      const profile: UserProfile = {
        sub: resolvedUsername,
        preferred_username: resolvedUsername,
        roles,
        email: userEmail
      }

      setSession(rawToken, profile, 'keycloak')
      closeAuthModal()
      return profile
    } catch (err: any) {
      // Resilient fallback: If seeded demo account or locally registered account matches, allow seamless login
      if (resolvedAccount && resolvedAccount.password === password) {
        const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: resolvedUsername, roles: resolvedAccount.roles, email: resolvedAccount.email }))}`
        const profile: UserProfile = {
          sub: resolvedUsername,
          preferred_username: resolvedUsername,
          roles: resolvedAccount.roles,
          email: resolvedAccount.email
        }

        setSession(mockJwt, profile, 'local')
        closeAuthModal()
        return profile
      }

      // If Keycloak returned an HTTP 401/400 (bad credentials) and credentials don't match local accounts
      if (err.response && (err.response.status === 401 || err.response.status === 400)) {
        throw new Error('Invalid username, email, or password in Keycloak realm.')
      }

      if (!resolvedAccount) {
        throw new Error(`Account "${rawIdentifier}" not found. (Use quick-select or register a new account)`)
      }

      if (resolvedAccount.password !== password) {
        throw new Error('Incorrect password provided.')
      }

      const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: resolvedUsername, roles: resolvedAccount.roles, email: resolvedAccount.email }))}`
      const profile: UserProfile = {
        sub: resolvedUsername,
        preferred_username: resolvedUsername,
        roles: resolvedAccount.roles,
        email: resolvedAccount.email
      }

      setSession(mockJwt, profile, 'local')
      closeAuthModal()
      return profile
    }
  }

  async function register(payload: RegisterPayload): Promise<UserProfile> {
    if (!payload.username || !payload.password) {
      throw new Error('Username and password are required.')
    }

    const cleanUsername = payload.username.trim()
    const cleanEmail = (payload.email || `${cleanUsername}@example.com`).trim().toLowerCase()

    const allAccounts = { ...SEEDED_CREDENTIALS, ...localRegisteredUsers.value }
    const existing = Object.entries(allAccounts).find(
      ([uname, acc]) => uname.toLowerCase() === cleanUsername.toLowerCase() || (acc.email && acc.email.toLowerCase() === cleanEmail)
    )

    if (existing) {
      throw new Error('An account with this username or email already exists.')
    }

    const apiOrigin = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')
    const registerUrl = apiOrigin ? `${apiOrigin}/api/auth/register` : '/api/auth/register'

    try {
      await axios.post(registerUrl, {
        username: cleanUsername,
        email: cleanEmail,
        password: payload.password,
        name: payload.name || cleanUsername
      }, { timeout: 5000 })
    } catch (apiErr: any) {
      if (apiErr.response?.status === 409) {
        throw new Error('An account with this username or email already exists.')
      }
      console.warn('[AuthStore] Backend registration notice:', apiErr.message)
    }

    localRegisteredUsers.value[cleanUsername] = {
      password: payload.password,
      roles: ['USER'],
      email: cleanEmail
    }

    return login(cleanUsername, payload.password)
  }

  function loginAsUser(username = 'user1') {
    const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: ['USER'] }))}`
    setSession(mockJwt, {
      sub: username,
      preferred_username: username,
      roles: ['USER']
    }, 'local')
  }

  function loginAsAdmin(username = 'admin1') {
    const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: ['ADMIN', 'USER'] }))}`
    setSession(mockJwt, {
      sub: username,
      preferred_username: username,
      roles: ['ADMIN', 'USER']
    }, 'local')
  }

  function loginAsDeveloper(username = 'dev1') {
    const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: ['DEVELOPER', 'USER'] }))}`
    setSession(mockJwt, {
      sub: username,
      preferred_username: username,
      roles: ['DEVELOPER', 'USER']
    }, 'local')
  }

  function loginAsManager(username = 'manager1') {
    setSession('mock-manager-token', {
      sub: username,
      preferred_username: username,
      roles: ['MANAGER', 'USER']
    }, 'local')
  }

  function logout() {
    accessToken.value = null
    user.value = null
  }

  return {
    accessToken,
    user,
    isAuthenticated,
    isAdmin,
    isManager,
    canManageProducts,
    isDeveloper,
    currentUserId,
    token,
    authMode,
    isAuthModalOpen,
    openAuthModal,
    closeAuthModal,
    toggleAuthModal,
    setSession,
    login,
    register,
    loginAsUser,
    loginAsAdmin,
    loginAsManager,
    loginAsDeveloper,
    logout
  }
})

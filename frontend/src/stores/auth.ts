import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'

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
  }

  /**
   * Hybrid Login:
   * 1. Attempts Keycloak OAuth 2.0 Direct Access Grants (Port 8180).
   * 2. If Keycloak is unreachable or offline, falls back seamlessly to pre-seeded local accounts.
   */
  async function login(username: string, password: string): Promise<UserProfile> {
    const keycloakTokenUrl = 'http://localhost:8180/realms/shopping-cart/protocol/openid-connect/token'

    try {
      const params = new URLSearchParams()
      params.append('client_id', 'shopping-cart-spa')
      params.append('grant_type', 'password')
      params.append('username', username)
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

      const profile: UserProfile = {
        sub: username,
        preferred_username: username,
        roles,
        email: `${username}@example.com`
      }

      setSession(rawToken, profile, 'keycloak')
      closeAuthModal()
      return profile
    } catch (err: any) {
      // Resilient fallback: If seeded demo account or locally registered account matches, allow seamless login
      const allAccounts = { ...SEEDED_CREDENTIALS, ...localRegisteredUsers.value }
      const match = allAccounts[username]

      if (match && match.password === password) {
        const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: match.roles, email: match.email }))}`
        const profile: UserProfile = {
          sub: username,
          preferred_username: username,
          roles: match.roles,
          email: match.email
        }

        setSession(mockJwt, profile, 'local')
        closeAuthModal()
        return profile
      }

      // If Keycloak returned an HTTP 401/400 (bad credentials) and credentials don't match local accounts
      if (err.response && (err.response.status === 401 || err.response.status === 400)) {
        throw new Error('Invalid username or password in Keycloak realm.')
      }

      if (!match) {
        throw new Error(`Account "${username}" not found. (Use quick-select or register a new account)`)
      }

      if (match.password !== password) {
        throw new Error('Incorrect password provided.')
      }

      const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: match.roles, email: match.email }))}`
      const profile: UserProfile = {
        sub: username,
        preferred_username: username,
        roles: match.roles,
        email: match.email
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

    localRegisteredUsers.value[payload.username] = {
      password: payload.password,
      roles: ['USER'],
      email: payload.email || `${payload.username}@example.com`
    }

    return login(payload.username, payload.password)
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

  function logout() {
    accessToken.value = null
    user.value = null
  }

  return {
    accessToken,
    user,
    isAuthenticated,
    isAdmin,
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
    loginAsDeveloper,
    logout
  }
})

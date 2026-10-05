import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserProfile {
  sub: string
  preferred_username: string
  roles: string[]
}

/**
 * In-Memory Authentication Store.
 *
 * <p>SECURITY MANDATE: The access token is stored strictly in JavaScript heap memory
 * and NEVER written to localStorage or sessionStorage. This protects session tokens
 * against client-side script exfiltration (XSS).
 */
export const useAuthStore = defineStore('auth', () => {
  // In-memory token storage only
  const accessToken = ref<string | null>(null)
  const user = ref<UserProfile | null>(null)

  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') || user.value?.roles.includes('ROLE_ADMIN') || false)
  const isDeveloper = computed(() => user.value?.roles.includes('DEVELOPER') || user.value?.roles.includes('ROLE_DEVELOPER') || false)
  const currentUserId = computed(() => user.value?.sub || '')
  const token = computed(() => accessToken.value)

  function setSession(tokenValue: string, profile: UserProfile) {
    accessToken.value = tokenValue
    user.value = profile
  }

  function loginAsUser(username = 'user1') {
    // Generates a mock Bearer JWT payload for local API interaction / testing
    const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: ['USER'] }))}`
    setSession(mockJwt, {
      sub: username,
      preferred_username: username,
      roles: ['USER']
    })
  }

  function loginAsAdmin(username = 'admin1') {
    const mockJwt = `demo.token.${btoa(JSON.stringify({ sub: username, roles: ['ADMIN', 'USER'] }))}`
    setSession(mockJwt, {
      sub: username,
      preferred_username: username,
      roles: ['ADMIN', 'USER']
    })
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
    setSession,
    loginAsUser,
    loginAsAdmin,
    logout
  }
})

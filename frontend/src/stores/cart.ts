import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiClient, ProblemDetail } from '../api/client'
import { AxiosError } from 'axios'

export interface CartItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface CartResponse {
  id: string
  userId: string
  version: number
  items: CartItem[]
  subtotal: number
  updatedAt: string
}

export interface CheckoutResponse {
  orderId: string
  userId: string
  idempotencyKey: string
  totalAmount: number
  status: string
  createdAt: string
  items: CartItem[]
}

export interface ProductMeta {
  productName?: string
  unitPrice?: number
}

const CART_STORAGE_KEY = 'aura_cart_v1'

function loadSavedCart(): CartResponse | null {
  if (typeof window === 'undefined' || !window.localStorage) return null
  try {
    const raw = window.localStorage.getItem(CART_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as CartResponse
      if (parsed && Array.isArray(parsed.items)) {
        return parsed
      }
    }
  } catch (e) {
    console.warn('[CartStore] Failed to parse local cart state:', e)
  }
  return null
}

function persistCart(data: CartResponse | null) {
  if (typeof window === 'undefined' || !window.localStorage) return
  try {
    if (data) {
      window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(data))
    } else {
      window.localStorage.removeItem(CART_STORAGE_KEY)
    }
  } catch (e) {
    console.warn('[CartStore] Failed to persist cart state:', e)
  }
}

export const useCartStore = defineStore('cart', () => {
  const cart = ref<CartResponse | null>(loadSavedCart())
  const loading = ref(false)
  const error = ref<string | null>(null)
  const isDrawerOpen = ref(false)

  const items = computed(() => cart.value?.items || [])
  const itemCount = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
  const subtotal = computed(() => cart.value?.subtotal || 0)
  const version = computed(() => cart.value?.version || 1)

  function openDrawer() {
    isDrawerOpen.value = true
  }

  function closeDrawer() {
    isDrawerOpen.value = false
  }

  function toggleDrawer() {
    isDrawerOpen.value = !isDrawerOpen.value
  }

  async function syncCartWithServer() {
    if (!cart.value || !Array.isArray(cart.value.items) || cart.value.items.length === 0) {
      return
    }

    try {
      const response = await apiClient.get<CartResponse>('/carts/me')
      const serverItems = response.data?.items || []

      // If server cart already has items, prioritize server aggregate
      if (serverItems.length > 0) {
        cart.value = response.data
        persistCart(cart.value)
        return
      }

      // If server cart is empty but client has local items, push all local items to server
      for (const item of cart.value.items) {
        try {
          await apiClient.post<CartResponse>('/carts/me/items', {
            productId: item.productId,
            quantity: item.quantity
          })
        } catch (itemErr) {
          console.warn('[CartStore] Error syncing item to server:', item.productId, itemErr)
        }
      }

      // Re-fetch authoritative cart snapshot from server
      const updatedResponse = await apiClient.get<CartResponse>('/carts/me')
      if (updatedResponse.data && Array.isArray(updatedResponse.data.items)) {
        cart.value = updatedResponse.data
        persistCart(cart.value)
      }
    } catch (err: any) {
      console.warn('[CartStore] syncCartWithServer error:', err?.message || err)
    }
  }

  async function fetchCart() {
    loading.value = true
    error.value = null
    try {
      const response = await apiClient.get<CartResponse>('/carts/me')
      if (response.data && Array.isArray(response.data.items)) {
        if (response.data.items.length > 0 || !cart.value || cart.value.items.length === 0) {
          cart.value = response.data
          persistCart(cart.value)
        } else if (cart.value && cart.value.items.length > 0 && response.data.items.length === 0) {
          // Local cart has items, but server cart is empty: reconcile to server
          await syncCartWithServer()
        }
      }
    } catch (err: any) {
      const axiosErr = err as AxiosError<ProblemDetail>
      error.value = axiosErr.response?.data?.detail || 'Failed to fetch cart'
      // Preserves existing cart state on network or server offline glitches
    } finally {
      loading.value = false
    }
  }

  /**
   * Optimistic UI Add-to-Basket (0ms Latency).
   * Instantly increments or appends the item in memory and local storage,
   * triggering UI badges and notifications immediately while server synchronizes.
   */
  async function addItem(productId: string, quantity = 1, meta?: ProductMeta) {
    error.value = null

    if (!cart.value) {
      cart.value = {
        id: 'cart-local-' + Date.now(),
        userId: 'current-user',
        version: 1,
        items: [],
        subtotal: 0,
        updatedAt: new Date().toISOString()
      }
    }

    const existingIndex = cart.value.items.findIndex(item => item.productId === productId)
    if (existingIndex >= 0) {
      const item = cart.value.items[existingIndex]
      item.quantity += quantity
      item.lineTotal = Math.round(item.unitPrice * item.quantity * 100) / 100
    } else {
      const unitPrice = meta?.unitPrice ?? 0
      const productName = meta?.productName ?? 'Hardware Item'
      cart.value.items.push({
        productId,
        productName,
        unitPrice,
        quantity,
        lineTotal: Math.round(unitPrice * quantity * 100) / 100
      })
    }

    cart.value.subtotal = Math.round(
      cart.value.items.reduce((sum, item) => sum + item.lineTotal, 0) * 100
    ) / 100
    cart.value.updatedAt = new Date().toISOString()
    persistCart(cart.value)

    // Background server synchronization
    try {
      const response = await apiClient.post<CartResponse>('/carts/me/items', {
        productId,
        quantity
      })
      if (response.data && Array.isArray(response.data.items)) {
        cart.value = response.data
        persistCart(cart.value)
      }
    } catch (err: any) {
      const axiosErr = err as AxiosError<ProblemDetail>
      console.warn('[CartStore] Background server sync:', axiosErr.message)
    }
  }

  async function updateQuantity(productId: string, quantity: number) {
    if (quantity <= 0) {
      return removeItem(productId)
    }
    error.value = null

    // Optimistic local update
    if (cart.value) {
      const item = cart.value.items.find(i => i.productId === productId)
      if (item) {
        item.quantity = quantity
        item.lineTotal = Math.round(item.unitPrice * quantity * 100) / 100
        cart.value.subtotal = Math.round(
          cart.value.items.reduce((sum, i) => sum + i.lineTotal, 0) * 100
        ) / 100
        cart.value.updatedAt = new Date().toISOString()
        persistCart(cart.value)
      }
    }

    try {
      const response = await apiClient.put<CartResponse>(`/carts/me/items/${productId}`, {
        quantity
      })
      if (response.data && Array.isArray(response.data.items)) {
        cart.value = response.data
        persistCart(cart.value)
      }
    } catch (err: any) {
      const axiosErr = err as AxiosError<ProblemDetail>
      console.warn('[CartStore] Background updateQuantity sync:', axiosErr.message)
    }
  }

  async function removeItem(productId: string) {
    error.value = null

    // Optimistic local update
    if (cart.value) {
      cart.value.items = cart.value.items.filter(i => i.productId !== productId)
      cart.value.subtotal = Math.round(
        cart.value.items.reduce((sum, i) => sum + i.lineTotal, 0) * 100
      ) / 100
      cart.value.updatedAt = new Date().toISOString()
      persistCart(cart.value)
    }

    try {
      const response = await apiClient.delete<CartResponse>(`/carts/me/items/${productId}`)
      if (response.data && Array.isArray(response.data.items)) {
        cart.value = response.data
        persistCart(cart.value)
      }
    } catch (err: any) {
      const axiosErr = err as AxiosError<ProblemDetail>
      console.warn('[CartStore] Background removeItem sync:', axiosErr.message)
    }
  }

  async function clearCart() {
    loading.value = true
    error.value = null
    try {
      await apiClient.delete('/carts/me')
    } catch (err: any) {
      const axiosErr = err as AxiosError<ProblemDetail>
      console.warn('[CartStore] Server clearCart:', axiosErr.message)
    } finally {
      if (cart.value) {
        cart.value.items = []
        cart.value.subtotal = 0
        cart.value.updatedAt = new Date().toISOString()
      }
      persistCart(cart.value)
      loading.value = false
    }
  }

  /**
   * Checkout Action.
   *
   * <p>PERMANENT RESOLUTION OF TD-T1-05:
   * The client explicitly generates a cryptographically random UUID Idempotency-Key
   * header on every checkout request, eliminating server-side key generation dependencies.
   */
  async function checkout(): Promise<CheckoutResponse> {
    loading.value = true
    error.value = null

    // Ensure local basket items are fully synchronized to the server's MongoDB cart before checkout
    await syncCartWithServer()

    // Client-side UUID generation for Idempotency-Key
    const idempotencyKey = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : 'idem-' + Math.random().toString(36).substring(2, 12)

    try {
      const response = await apiClient.post<CheckoutResponse>('/orders/checkout', null, {
        headers: {
          'Idempotency-Key': idempotencyKey
        }
      })
      // Clear cart locally upon successful checkout confirmation
      if (cart.value) {
        cart.value.items = []
        cart.value.subtotal = 0
        cart.value.updatedAt = new Date().toISOString()
      }
      persistCart(cart.value)
      return response.data
    } catch (err: any) {
      const axiosErr = err as AxiosError<ProblemDetail>
      if (axiosErr.response?.status === 409) {
        error.value = 'Cart state conflict detected: The cart was modified concurrently. Please review your cart and retry.'
      } else {
        error.value = axiosErr.response?.data?.detail || 'Checkout failed'
      }
      throw err
    } finally {
      loading.value = false
    }
  }

  return {
    cart,
    loading,
    error,
    items,
    itemCount,
    subtotal,
    version,
    isDrawerOpen,
    openDrawer,
    closeDrawer,
    toggleDrawer,
    fetchCart,
    syncCartWithServer,
    addItem,
    updateQuantity,
    removeItem,
    clearCart,
    checkout
  }
})

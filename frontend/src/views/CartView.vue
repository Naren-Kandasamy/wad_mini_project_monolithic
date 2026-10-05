<template>
  <div class="container" style="max-width: 800px;">
    <h1 style="font-size: 1.5rem; font-weight: 700; margin-bottom: 1.5rem;">Your Shopping Cart</h1>

    <div v-if="cartStore.error" class="card" style="border-color: #fecaca; background: #fef2f2; color: #991b1b; margin-bottom: 1.5rem;">
      <strong>⚠️ {{ cartStore.error }}</strong>
    </div>

    <div v-if="checkoutSuccess" class="card" style="border-color: #bbf7d0; background: #f0fdf4; color: #166534; margin-bottom: 1.5rem;">
      <strong>🎉 Checkout Confirmed!</strong>
      <p style="font-size: 0.9rem; margin-top: 0.25rem;">
        Order <code>{{ checkoutSuccess.orderId }}</code> confirmed for ${{ Number(checkoutSuccess.totalAmount).toFixed(2) }}.
      </p>
      <router-link to="/orders" class="btn btn-primary" style="margin-top: 0.75rem; font-size: 0.8rem;">
        View Orders
      </router-link>
    </div>

    <div v-if="cartStore.loading && !cartStore.cart" style="text-align: center; padding: 3rem; color: var(--text-muted);">
      Loading cart...
    </div>

    <div v-else-if="cartStore.items.length === 0" class="card" style="text-align: center; padding: 3rem;">
      <p style="color: var(--text-muted); margin-bottom: 1rem;">Your shopping cart is currently empty.</p>
      <router-link to="/" class="btn btn-primary">Browse Products</router-link>
    </div>

    <div v-else class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); padding-bottom: 0.75rem; margin-bottom: 1rem;">
        <span style="font-size: 0.875rem; color: var(--text-muted);">
          Cart Version: <code>v{{ cartStore.version }}</code> (Optimistic Concurrency Guarded)
        </span>
        <button @click="cartStore.clearCart" class="btn btn-outline" style="font-size: 0.75rem; color: var(--danger);">
          Clear Cart
        </button>
      </div>

      <div v-for="item in cartStore.items" :key="item.productId" style="display: flex; justify-content: space-between; align-items: center; padding: 0.75rem 0; border-bottom: 1px solid var(--border-color);">
        <div>
          <h4 style="font-weight: 600;">{{ item.productName }}</h4>
          <span style="font-size: 0.85rem; color: var(--text-muted);">
            ${{ Number(item.unitPrice).toFixed(2) }} each
          </span>
        </div>

        <div style="display: flex; align-items: center; gap: 1rem;">
          <div style="display: flex; align-items: center; gap: 0.5rem;">
            <button @click="cartStore.updateQuantity(item.productId, item.quantity - 1)" class="btn btn-outline" style="padding: 0.2rem 0.5rem; font-size: 0.75rem;">
              -
            </button>
            <span style="font-weight: 600; min-width: 1.5rem; text-align: center;">{{ item.quantity }}</span>
            <button @click="cartStore.updateQuantity(item.productId, item.quantity + 1)" class="btn btn-outline" style="padding: 0.2rem 0.5rem; font-size: 0.75rem;">
              +
            </button>
          </div>

          <span style="font-weight: 700; min-width: 4rem; text-align: right;">
            ${{ Number(item.lineTotal).toFixed(2) }}
          </span>

          <button @click="cartStore.removeItem(item.productId)" class="btn btn-outline" style="padding: 0.2rem 0.5rem; font-size: 0.75rem; color: var(--danger);">
            ✕
          </button>
        </div>
      </div>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 1.5rem; padding-top: 1rem; border-top: 2px solid var(--border-color);">
        <div>
          <span style="font-size: 1rem; color: var(--text-muted);">Subtotal:</span>
          <span style="font-size: 1.5rem; font-weight: 700; margin-left: 0.5rem; color: var(--primary);">
            ${{ Number(cartStore.subtotal).toFixed(2) }}
          </span>
        </div>

        <button @click="handleCheckout" class="btn btn-primary" :disabled="cartStore.loading" style="font-size: 1rem; padding: 0.75rem 1.5rem;">
          {{ cartStore.loading ? 'Processing...' : '💳 Checkout Now' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useCartStore, CheckoutResponse } from '../stores/cart'
import { useAuthStore } from '../stores/auth'

const cartStore = useCartStore()
const authStore = useAuthStore()
const checkoutSuccess = ref<CheckoutResponse | null>(null)

async function handleCheckout() {
  checkoutSuccess.value = null
  try {
    const res = await cartStore.checkout()
    checkoutSuccess.value = res
  } catch (err) {
    console.error('Checkout error', err)
  }
}

onMounted(() => {
  if (!authStore.isAuthenticated) {
    authStore.loginAsUser('user1')
  }
  cartStore.fetchCart()
})
</script>

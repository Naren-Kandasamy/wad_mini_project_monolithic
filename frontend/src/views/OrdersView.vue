<template>
  <div class="container" style="max-width: 800px;">
    <div style="margin-bottom: 1.5rem; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <h1 style="font-size: 1.5rem; font-weight: 700;">My Order History</h1>
        <p style="color: var(--text-muted); font-size: 0.9rem;">Server-authoritative orders for authenticated user</p>
      </div>
      <button @click="loadOrders" class="btn btn-outline" style="font-size: 0.8rem;">
        🔄 Refresh
      </button>
    </div>

    <div v-if="loading" style="text-align: center; padding: 3rem; color: var(--text-muted);">
      Loading orders...
    </div>

    <div v-else-if="orders.length === 0" class="card" style="text-align: center; padding: 3rem;">
      <p style="color: var(--text-muted); margin-bottom: 1rem;">You have not placed any orders yet.</p>
      <router-link to="/" class="btn btn-primary">Browse Products</router-link>
    </div>

    <div v-else style="display: flex; flex-direction: column; gap: 1rem;">
      <div v-for="order in orders" :key="order.id" class="card">
        <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 0.75rem;">
          <div>
            <span style="font-weight: 700; font-size: 1rem;">Order #{{ order.id }}</span>
            <span style="font-size: 0.8rem; color: var(--text-muted); margin-left: 0.75rem;">
              {{ new Date(order.createdAt).toLocaleString() }}
            </span>
          </div>
          <span class="badge badge-success">{{ order.status }}</span>
        </div>

        <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.5rem;">
          Idempotency Key: <code>{{ order.idempotencyKey }}</code>
        </div>

        <div v-for="item in order.items" :key="item.productId" style="display: flex; justify-content: space-between; font-size: 0.9rem; padding: 0.25rem 0;">
          <span>{{ item.quantity }}x {{ item.productName }}</span>
          <span style="font-weight: 500;">${{ (Number(item.unitPrice) * item.quantity).toFixed(2) }}</span>
        </div>

        <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--border-color); margin-top: 0.75rem; padding-top: 0.5rem;">
          <span style="font-weight: 600;">Total:</span>
          <span style="font-size: 1.1rem; font-weight: 700; color: var(--primary);">
            ${{ Number(order.totalAmount).toFixed(2) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { apiClient } from '../api/client'
import { useAuthStore } from '../stores/auth'

interface OrderItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
}

interface Order {
  id: string
  userId: string
  idempotencyKey: string
  items: OrderItem[]
  totalAmount: number
  status: string
  createdAt: string
}

const orders = ref<Order[]>([])
const loading = ref(false)
const authStore = useAuthStore()

async function loadOrders() {
  loading.value = true
  try {
    const res = await apiClient.get<Order[]>('/orders')
    orders.value = res.data
  } catch (err) {
    console.error('Failed to load orders', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!authStore.isAuthenticated) {
    authStore.loginAsUser('user1')
  }
  loadOrders()
})
</script>

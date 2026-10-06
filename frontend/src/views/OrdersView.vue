<template>
  <div class="orders-page container-wide">
    <div class="orders-header">
      <div>
        <h1 class="orders-title font-display">My Order History</h1>
        <p class="orders-subtitle">Track confirmed orders and inspect cryptographic idempotency receipts.</p>
      </div>
      <button
        type="button"
        class="btn-secondary"
        :disabled="loading"
        @click="loadOrders"
      >
        <span>Refresh Orders</span>
      </button>
    </div>

    <!-- Loading Shimmer -->
    <div v-if="loading" class="orders-loading">
      <div v-for="n in 3" :key="n" class="skeleton-shimmer h-32 w-full mb-4"></div>
    </div>

    <!-- Offline Service Error State -->
    <ServiceOfflineCard
      v-else-if="connectionError"
      title="Orders Service Offline"
      description="We couldn't connect to the backend to fetch your order history. The monolithic service may be restarting or unreachable."
      endpoint="/api/orders"
      :onRetry="loadOrders"
    />

    <!-- Unauthenticated State -->
    <div v-else-if="!authStore.isAuthenticated" class="empty-orders ceramic-card">
      <div class="wax-seal empty-seal">
        <SvgIcon name="user" size="32" color="var(--accent-clay)" />
      </div>
      <h2 class="empty-title font-display">Sign In Required</h2>
      <p class="empty-desc">
        Please sign in with your account to view your past orders and receipts.
      </p>
      <button type="button" class="btn-clay-terracotta mt-4" @click="authStore.openAuthModal()">
        <span>Sign In</span>
        <SvgIcon name="arrow-right" size="18" />
      </button>
    </div>

    <!-- Empty State -->
    <div v-else-if="orders.length === 0" class="empty-orders ceramic-card">
      <div class="wax-seal empty-seal">
        <SvgIcon name="package" size="32" color="var(--accent-clay)" />
      </div>
      <h2 class="empty-title font-display">No Orders Found</h2>
      <p class="empty-desc">
        You haven't placed any orders yet. Visit our catalog to discover curated artisan essentials.
      </p>
      <router-link to="/" class="btn-clay-terracotta mt-4">
        <span>Browse Catalog</span>
        <SvgIcon name="arrow-right" size="18" />
      </router-link>
    </div>

    <!-- Orders List -->
    <div v-else class="orders-list">
      <div
        v-for="order in orders"
        :key="order.id"
        class="order-card ceramic-card"
      >
        <!-- Order Card Header -->
        <div class="order-card-header">
          <div class="order-primary-info">
            <span class="order-number font-display">Order #{{ order.id }}</span>
            <span class="order-timestamp">{{ formatDate(order.createdAt) }}</span>
          </div>
          <span class="status-badge embossed-badge" :class="`status-${order.status.toLowerCase()}`">
            {{ order.status }}
          </span>
        </div>

        <!-- Idempotency Tracking Ribbon -->
        <div class="order-receipt-ribbon">
          <span class="receipt-label">Idempotency-Key:</span>
          <code class="receipt-code">{{ order.idempotencyKey }}</code>
        </div>

        <!-- Order Items -->
        <div class="order-items-table">
          <div
            v-for="item in order.items"
            :key="item.productId"
            class="order-item-row"
          >
            <div class="item-name-group">
              <span class="item-qty-tag">{{ item.quantity }}x</span>
              <span class="item-title">{{ item.productName }}</span>
            </div>
            <span class="item-price-calc font-display">
              ${{ (Number(item.unitPrice) * item.quantity).toFixed(2) }}
            </span>
          </div>
        </div>

        <!-- Order Card Footer -->
        <div class="order-card-footer">
          <div class="order-user-tag">
            <span>Customer: {{ order.userId }}</span>
          </div>
          <div class="order-total-box">
            <span class="total-label">Total Paid:</span>
            <span class="total-val font-display">${{ Number(order.totalAmount).toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { apiClient } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import SvgIcon from '../components/SvgIcon.vue'
import ServiceOfflineCard from '../components/ServiceOfflineCard.vue'

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
const connectionError = ref(false)
const authStore = useAuthStore()
const toastStore = useToastStore()

function formatDate(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return dateStr
  }
}

async function loadOrders() {
  if (!authStore.isAuthenticated) {
    authStore.openAuthModal()
    return
  }

  loading.value = true
  connectionError.value = false
  try {
    const res = await apiClient.get<Order[]>('/orders')
    orders.value = res.data
  } catch (err: any) {
    connectionError.value = true
    // Only developers receive diagnostic toast telemetry
    if (authStore.isDeveloper) {
      toastStore.show(`Orders API failed: ${err.message || 'Network Error'}`, 'error')
    }
  } finally {
    loading.value = false
  }
}

watch(
  () => authStore.isAuthenticated,
  (authed) => {
    if (authed) {
      loadOrders()
    } else {
      orders.value = []
      connectionError.value = false
    }
  }
)

onMounted(() => {
  if (authStore.isAuthenticated) {
    loadOrders()
  }
})
</script>

<style scoped>
.orders-page {
  padding-top: 2rem;
  padding-bottom: 5rem;
  max-width: 900px;
}

.orders-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 2rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.orders-title {
  font-size: 2.2rem;
  font-weight: 700;
  color: var(--surface-dark);
  margin-bottom: 0.35rem;
}

.orders-subtitle {
  font-size: 0.95rem;
  color: var(--text-muted);
}

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.order-card {
  padding: 1.75rem;
}

.order-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border-subtle);
}

.order-primary-info {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  flex-wrap: wrap;
}

.order-number {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--surface-dark);
}

.order-timestamp {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.status-badge {
  padding: 0.3rem 0.8rem;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.status-created {
  background: #EAF4EE;
  color: #166534;
  border-color: rgba(22, 101, 52, 0.2);
}

.status-confirmed {
  background: #EBF3FB;
  color: #1E40AF;
  border-color: rgba(30, 64, 175, 0.2);
}

.status-shipped {
  background: #FDF4E7;
  color: #9A6700;
  border-color: rgba(154, 103, 0, 0.2);
}

.order-receipt-ribbon {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.65rem 0.85rem;
  background: var(--canvas-alt);
  border-radius: 0.65rem;
  margin: 1rem 0;
  font-size: 0.8rem;
}

.receipt-label {
  color: var(--text-secondary);
  font-weight: 600;
}

.receipt-code {
  font-family: monospace;
  color: var(--surface-dark);
}

.order-items-table {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.5rem 0;
}

.order-item-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.95rem;
}

.item-name-group {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
}

.item-qty-tag {
  font-weight: 700;
  color: var(--accent-terracotta);
}

.item-title {
  color: var(--text-primary);
  font-weight: 500;
}

.item-price-calc {
  font-weight: 600;
  color: var(--surface-dark);
}

.order-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border-subtle);
}

.order-user-tag {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.order-total-box {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.total-label {
  font-size: 0.95rem;
  color: var(--text-secondary);
  font-weight: 600;
}

.total-val {
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--surface-dark);
}

.empty-orders {
  padding: 4rem 2rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-seal {
  width: 68px;
  height: 68px;
  margin-bottom: 1.25rem;
}

.empty-title {
  font-size: 1.5rem;
  color: var(--surface-dark);
  margin-bottom: 0.5rem;
}

.empty-desc {
  font-size: 0.9rem;
  color: var(--text-muted);
  max-width: 360px;
  line-height: 1.5;
}
</style>

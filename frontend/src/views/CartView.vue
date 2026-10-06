<template>
  <div class="cart-page container-wide">
    <div class="cart-header">
      <h1 class="cart-title font-display">Review Your Basket</h1>
      <p class="cart-subtitle">Verify selected pieces before placing your secure, idempotent order.</p>
    </div>

    <!-- Error Alert Banner -->
    <div v-if="cartStore.error" class="alert-banner alert-error">
      <SvgIcon name="close" size="18" color="var(--accent-terracotta)" />
      <span>{{ cartStore.error }}</span>
    </div>

    <!-- Order Confirmed Success Screen -->
    <div v-if="checkoutSuccess" class="success-card tactile-card">
      <div class="success-seal embossed-seal">
        <SvgIcon name="check" size="32" color="var(--accent-success)" />
      </div>
      <h2 class="success-title font-display">Order Successfully Placed</h2>
      <p class="success-text">
        Thank you for your order! Your transaction has been cryptographically confirmed and assigned Order ID:
      </p>
      <div class="order-id-badge embossed-badge">
        <span>Order #{{ checkoutSuccess.orderId }}</span>
      </div>
      <p class="order-total-text">
        Total Charged: <strong class="text-terracotta">${{ Number(checkoutSuccess.totalAmount).toFixed(2) }}</strong>
      </p>
      <div class="success-actions">
        <router-link to="/orders" class="btn-forest">
          <SvgIcon name="package" size="18" />
          <span>View Order History</span>
        </router-link>
        <router-link to="/" class="btn-secondary">
          <span>Continue Shopping</span>
        </router-link>
      </div>
    </div>

    <!-- Loading State -->
    <div v-else-if="cartStore.loading && !cartStore.cart" class="cart-loading">
      <div class="skeleton-shimmer h-12 w-3/4 mb-4"></div>
      <div class="skeleton-shimmer h-48 w-full"></div>
    </div>

    <!-- Empty State -->
    <div v-else-if="cartStore.items.length === 0" class="empty-cart-card ceramic-card">
      <div class="wax-seal empty-cart-seal">
        <SvgIcon name="bag" size="32" color="var(--accent-clay)" />
      </div>
      <h2 class="empty-title font-display">Your basket is empty</h2>
      <p class="empty-desc">
        You have no items in your basket. Explore our catalog to discover precision computing devices and hardware.
      </p>
      <router-link to="/" class="btn-clay-terracotta mt-4">
        <span>Browse Catalog</span>
        <SvgIcon name="arrow-right" size="18" />
      </router-link>
    </div>

    <!-- 2-Column Checkout Layout -->
    <div v-else class="cart-layout-grid">
      <!-- Left Column: Items List -->
      <div class="cart-items-column">
        <div class="ceramic-card items-card">
          <div class="items-card-header">
            <div class="flex items-center gap-2">
              <span class="items-count-tag font-display">{{ cartStore.itemCount }} Items</span>
              <span class="version-tag embossed-badge">
                Concurrency Guarded v{{ cartStore.version }}
              </span>
            </div>
            <button
              type="button"
              class="clear-cart-link"
              @click="cartStore.clearCart"
            >
              Clear Basket
            </button>
          </div>

          <div class="items-table">
            <div
              v-for="item in cartStore.items"
              :key="item.productId"
              class="cart-row"
            >
              <div class="item-meta">
                <h4 class="item-title">{{ item.productName }}</h4>
                <span class="unit-price">${{ Number(item.unitPrice).toFixed(2) }} each</span>
              </div>

              <div class="item-stepper-wrap">
                <div class="stepper-box">
                  <button
                    type="button"
                    class="stepper-btn"
                    :disabled="cartStore.loading"
                    @click="handleDecrement(item)"
                    aria-label="Decrease quantity"
                  >
                    <SvgIcon name="minus" size="14" />
                  </button>
                  <span class="stepper-value">{{ item.quantity }}</span>
                  <button
                    type="button"
                    class="stepper-btn"
                    :disabled="cartStore.loading"
                    @click="handleIncrement(item)"
                    aria-label="Increase quantity"
                  >
                    <SvgIcon name="plus" size="14" />
                  </button>
                </div>

                <div class="item-subtotal-box">
                  <span class="line-price font-display">${{ Number(item.lineTotal).toFixed(2) }}</span>
                  <button
                    type="button"
                    class="remove-icon-btn"
                    @click="handleRemove(item.productId)"
                    title="Remove item"
                  >
                    <SvgIcon name="trash" size="15" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="back-link-box">
          <router-link to="/" class="continue-link">
            <SvgIcon name="chevron-left" size="16" />
            <span>Continue Shopping</span>
          </router-link>
        </div>
      </div>

      <!-- Right Column: Sticky Order Summary & Checkout -->
      <div class="summary-column">
        <div class="ceramic-card summary-card">
          <h3 class="summary-title font-display">Order Summary</h3>

          <div class="summary-breakdown">
            <div class="breakdown-row">
              <span>Items Subtotal</span>
              <span class="font-display">${{ Number(cartStore.subtotal).toFixed(2) }}</span>
            </div>
            <div class="breakdown-row">
              <span>Shipping &amp; Handling</span>
              <span class="free-shipping">Complimentary</span>
            </div>
            <div class="breakdown-row">
              <span>Estimated Tax</span>
              <span>$0.00</span>
            </div>
          </div>

          <div class="summary-total-row">
            <span class="total-label">Estimated Total</span>
            <span class="total-amount font-display">${{ Number(cartStore.subtotal).toFixed(2) }}</span>
          </div>

          <!-- Checkout Primary CTA -->
          <button
            type="button"
            class="btn-clay-terracotta checkout-btn"
            :disabled="cartStore.loading"
            @click="handleCheckout"
          >
            <template v-if="cartStore.loading">
              <span>Processing Order...</span>
            </template>
            <template v-else>
              <span>Confirm &amp; Place Order</span>
              <SvgIcon name="arrow-right" size="18" />
            </template>
          </button>

          <!-- Security Badges -->
          <div class="security-guarantees">
            <div class="guarantee-item">
              <SvgIcon name="shield" size="16" color="var(--accent-sage)" />
              <span>Idempotency-Key (RFC 7231) Defended</span>
            </div>
            <div class="guarantee-item">
              <SvgIcon name="check" size="16" color="var(--accent-sage)" />
              <span>Optimistic Concurrency Protection</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useCartStore, CheckoutResponse, CartItem } from '../stores/cart'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'
import SvgIcon from '../components/SvgIcon.vue'

const cartStore = useCartStore()
const authStore = useAuthStore()
const toastStore = useToastStore()
const checkoutSuccess = ref<CheckoutResponse | null>(null)

async function handleCheckout() {
  if (!authStore.isAuthenticated) {
    authStore.openAuthModal()
    toastStore.show('Please sign in to place your order', 'info')
    return
  }

  checkoutSuccess.value = null
  try {
    const res = await cartStore.checkout()
    checkoutSuccess.value = res
    toastStore.show('Order successfully confirmed!', 'success')
  } catch (err: any) {
    if (authStore.isDeveloper) {
      toastStore.show(cartStore.error || 'Checkout could not be processed', 'error')
    } else {
      toastStore.show('Could not process checkout at this time. Please try again.', 'error')
    }
  }
}

async function handleIncrement(item: CartItem) {
  try {
    await cartStore.updateQuantity(item.productId, item.quantity + 1)
  } catch (err) {
    if (authStore.isDeveloper) {
      toastStore.show('Failed to update quantity', 'error')
    }
  }
}

async function handleDecrement(item: CartItem) {
  try {
    if (item.quantity <= 1) {
      await cartStore.removeItem(item.productId)
      toastStore.show('Item removed from basket', 'info')
    } else {
      await cartStore.updateQuantity(item.productId, item.quantity - 1)
    }
  } catch (err) {
    if (authStore.isDeveloper) {
      toastStore.show('Failed to update quantity', 'error')
    }
  }
}

async function handleRemove(productId: string) {
  try {
    await cartStore.removeItem(productId)
    toastStore.show('Item removed from basket', 'info')
  } catch (err) {
    if (authStore.isDeveloper) {
      toastStore.show('Failed to remove item', 'error')
    }
  }
}

watch(
  () => authStore.isAuthenticated,
  (authed) => {
    if (authed) {
      cartStore.fetchCart()
    }
  }
)

onMounted(() => {
  if (authStore.isAuthenticated) {
    cartStore.fetchCart()
  }
})
</script>

<style scoped>
.cart-page {
  padding-top: 2rem;
  padding-bottom: 5rem;
}

.cart-header {
  margin-bottom: 2rem;
}

.cart-title {
  font-size: 2.2rem;
  font-weight: 700;
  color: var(--surface-dark);
  margin-bottom: 0.35rem;
}

.cart-subtitle {
  font-size: 0.95rem;
  color: var(--text-muted);
}

.alert-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-radius: 0.875rem;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
}

.alert-error {
  background: #FDF2F0;
  border: 1px solid rgba(200, 109, 81, 0.2);
  color: #943A24;
}

/* Success Card */
.success-card {
  text-align: center;
  padding: 4rem 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 620px;
  margin: 0 auto;
}

.success-seal {
  width: 72px;
  height: 72px;
  margin-bottom: 1.5rem;
  background: #EAF4EE;
  border-color: rgba(46, 125, 82, 0.2);
}

.success-title {
  font-size: 2rem;
  color: var(--surface-dark);
  margin-bottom: 0.75rem;
}

.success-text {
  font-size: 0.95rem;
  color: var(--text-secondary);
  max-width: 460px;
  line-height: 1.5;
  margin-bottom: 1.25rem;
}

.order-id-badge {
  background: var(--canvas-alt);
  color: var(--surface-dark);
  font-family: monospace;
  font-size: 0.95rem;
  padding: 0.4rem 1rem;
  margin-bottom: 1.25rem;
}

.text-terracotta {
  color: var(--accent-terracotta);
}

.order-total-text {
  font-size: 1.1rem;
  margin-bottom: 2rem;
}

.success-actions {
  display: flex;
  gap: 1rem;
}

/* Empty State */
.empty-cart-card {
  padding: 4.5rem 2rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 580px;
  margin: 0 auto;
}

.empty-cart-seal {
  width: 68px;
  height: 68px;
  margin-bottom: 1.5rem;
}

.empty-title {
  font-size: 1.6rem;
  color: var(--surface-dark);
  margin-bottom: 0.5rem;
}

.empty-desc {
  font-size: 0.95rem;
  color: var(--text-muted);
  max-width: 360px;
  line-height: 1.5;
}

/* 2-Column Grid */
.cart-layout-grid {
  display: grid;
  grid-template-columns: 1.7fr 1fr;
  gap: 2.5rem;
  align-items: start;
}

.items-card {
  padding: 1.75rem;
}

.items-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--border-subtle);
}

.items-count-tag {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--surface-dark);
}

.version-tag {
  background: var(--canvas-alt);
  color: var(--text-muted);
  font-size: 0.7rem;
  padding: 0.2rem 0.6rem;
}

.clear-cart-link {
  background: transparent;
  border: none;
  font-size: 0.8rem;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.15s ease;
}

.clear-cart-link:hover {
  color: var(--accent-danger);
}

.items-table {
  display: flex;
  flex-direction: column;
}

.cart-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.35rem 0;
  border-bottom: 1px solid var(--border-subtle);
  gap: 1.5rem;
}

.item-meta {
  flex: 1;
}

.item-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.2rem;
}

.unit-price {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.item-stepper-wrap {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.stepper-box {
  display: inline-flex;
  align-items: center;
  background: var(--canvas-alt);
  border-radius: 0.65rem;
  padding: 0.15rem;
  border: 1px solid var(--border-subtle);
}

.stepper-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  border-radius: 0.45rem;
  transition: all 0.15s ease;
}

.stepper-btn:hover:not(:disabled) {
  background: #FFFFFF;
  color: var(--text-primary);
}

.stepper-value {
  font-size: 0.85rem;
  font-weight: 600;
  min-width: 32px;
  text-align: center;
}

.item-subtotal-box {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.line-price {
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--surface-dark);
  min-width: 70px;
  text-align: right;
}

.remove-icon-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-muted);
  padding: 4px;
  transition: color 0.15s ease;
}

.remove-icon-btn:hover {
  color: var(--accent-danger);
}

.back-link-box {
  margin-top: 1.25rem;
}

.continue-link {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 600;
  transition: color 0.15s ease;
}

.continue-link:hover {
  color: var(--accent-terracotta);
}

/* Summary Card */
.summary-card {
  padding: 1.75rem;
  position: sticky;
  top: 96px;
}

.summary-title {
  font-size: 1.3rem;
  color: var(--surface-dark);
  margin-bottom: 1.25rem;
}

.summary-breakdown {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--border-subtle);
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.breakdown-row {
  display: flex;
  justify-content: space-between;
}

.free-shipping {
  color: var(--accent-success);
  font-weight: 600;
}

.summary-total-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 1.25rem 0;
}

.total-label {
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--text-primary);
}

.total-amount {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--surface-dark);
}

.checkout-btn {
  width: 100%;
  padding: 0.9rem 1.25rem;
  font-size: 0.95rem;
}

.security-guarantees {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.guarantee-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
}

@media (max-width: 860px) {
  .cart-layout-grid {
    grid-template-columns: 1fr;
  }
}
</style>

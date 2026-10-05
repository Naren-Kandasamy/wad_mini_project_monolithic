<template>
  <div class="container">
    <div style="margin-bottom: 1.5rem; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <h1 style="font-size: 1.5rem; font-weight: 700;">Product Catalog</h1>
        <p style="color: var(--text-muted); font-size: 0.9rem;">Browse available items seeded in the monolith database</p>
      </div>
      <button @click="loadProducts" class="btn btn-outline" style="font-size: 0.8rem;">
        🔄 Refresh
      </button>
    </div>

    <div v-if="loading" style="text-align: center; padding: 3rem; color: var(--text-muted);">
      Loading catalog...
    </div>

    <div v-else-if="products.length === 0" class="card" style="text-align: center; padding: 3rem;">
      <p style="color: var(--text-muted);">No products found in the catalog.</p>
    </div>

    <div v-else class="grid-3">
      <div v-for="product in products" :key="product.id" class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
        <div>
          <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 0.5rem;">
            <h3 style="font-size: 1.1rem; font-weight: 600;">{{ product.name }}</h3>
            <span class="badge">{{ product.sku }}</span>
          </div>
          <p style="color: var(--text-muted); font-size: 0.875rem; margin-bottom: 1rem;">
            {{ product.description }}
          </p>
        </div>
        <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--border-color); padding-top: 0.75rem;">
          <span style="font-size: 1.25rem; font-weight: 700; color: var(--primary);">
            ${{ Number(product.price).toFixed(2) }}
          </span>
          <button @click="addToCart(product.id)" class="btn btn-primary" :disabled="addingId === product.id">
            {{ addingId === product.id ? 'Adding...' : 'Add to Cart' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { apiClient } from '../api/client'
import { useCartStore } from '../stores/cart'
import { useAuthStore } from '../stores/auth'

interface Product {
  id: string
  name: string
  description: string
  price: number
  sku: string
  active: boolean
}

const products = ref<Product[]>([])
const loading = ref(false)
const addingId = ref<string | null>(null)

const cartStore = useCartStore()
const authStore = useAuthStore()

async function loadProducts() {
  loading.value = true
  try {
    const res = await apiClient.get<Product[]>('/products')
    products.value = res.data
  } catch (err) {
    console.error('Failed to load products', err)
  } finally {
    loading.value = false
  }
}

async function addToCart(productId: string) {
  if (!authStore.isAuthenticated) {
    authStore.loginAsUser('user1')
  }
  addingId.value = productId
  try {
    await cartStore.addItem(productId, 1)
  } catch (err) {
    console.error('Failed to add item to cart', err)
  } finally {
    addingId.value = null
  }
}

onMounted(() => {
  loadProducts()
})
</script>

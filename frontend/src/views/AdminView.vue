<template>
  <div class="container" style="max-width: 800px;">
    <h1 style="font-size: 1.5rem; font-weight: 700; margin-bottom: 1.5rem;">Admin Management Portal</h1>

    <div v-if="!authStore.isAdmin" class="card" style="border-color: #fecaca; background: #fef2f2; color: #991b1b; padding: 2rem; text-align: center;">
      <h3>🔒 Access Denied</h3>
      <p style="margin-top: 0.5rem; font-size: 0.9rem;">
        You must have the <code>ROLE_ADMIN</code> authority to view this page.
      </p>
      <button @click="authStore.loginAsAdmin('admin1')" class="btn btn-primary" style="margin-top: 1rem;">
        Login as Admin
      </button>
    </div>

    <div v-else>
      <!-- System Diagnostics -->
      <div class="card" style="margin-bottom: 1.5rem;">
        <h3 style="font-size: 1.1rem; font-weight: 600; margin-bottom: 0.75rem;">System Health & Diagnostics</h3>
        <div v-if="statsLoading" style="color: var(--text-muted); font-size: 0.9rem;">Loading system stats...</div>
        <div v-else-if="stats" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1rem;">
          <div style="background: var(--bg-color); padding: 0.75rem; border-radius: 0.375rem;">
            <div style="font-size: 0.75rem; color: var(--text-muted);">System Status</div>
            <div style="font-weight: 700; color: var(--success); font-size: 1.1rem;">{{ stats.status }}</div>
          </div>
          <div style="background: var(--bg-color); padding: 0.75rem; border-radius: 0.375rem;">
            <div style="font-size: 0.75rem; color: var(--text-muted);">Active Node</div>
            <div style="font-weight: 700; font-size: 1.1rem;">{{ stats.node }}</div>
          </div>
          <div style="background: var(--bg-color); padding: 0.75rem; border-radius: 0.375rem;">
            <div style="font-size: 0.75rem; color: var(--text-muted);">Health</div>
            <div style="font-weight: 700; font-size: 1.1rem;">{{ stats.system }}</div>
          </div>
        </div>
      </div>

      <!-- Add New Product Form -->
      <div class="card">
        <h3 style="font-size: 1.1rem; font-weight: 600; margin-bottom: 1rem;">Add New Product to Catalog</h3>

        <div v-if="formSuccess" class="card" style="background: #f0fdf4; border-color: #bbf7d0; color: #166534; margin-bottom: 1rem; padding: 0.75rem;">
          {{ formSuccess }}
        </div>

        <div v-if="formError" class="card" style="background: #fef2f2; border-color: #fecaca; color: #991b1b; margin-bottom: 1rem; padding: 0.75rem;">
          {{ formError }}
        </div>

        <form @submit.prevent="createProduct" style="display: flex; flex-direction: column; gap: 1rem;">
          <div>
            <label style="display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.25rem;">Name</label>
            <input v-model="form.name" type="text" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem;" placeholder="e.g. Wireless Trackball" />
          </div>

          <div>
            <label style="display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.25rem;">Description</label>
            <textarea v-model="form.description" rows="2" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem;" placeholder="e.g. Ergonomic thumb-controlled trackball"></textarea>
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
            <div>
              <label style="display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.25rem;">Price ($)</label>
              <input v-model.number="form.price" type="number" step="0.01" min="0.01" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem;" placeholder="49.99" />
            </div>
            <div>
              <label style="display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.25rem;">SKU</label>
              <input v-model="form.sku" type="text" required style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color); border-radius: 0.375rem;" placeholder="TRACKBALL-01" />
            </div>
          </div>

          <button type="submit" class="btn btn-primary" :disabled="submitting" style="align-self: flex-start;">
            {{ submitting ? 'Adding...' : '➕ Create Product' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { apiClient } from '../api/client'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

const stats = ref<any>(null)
const statsLoading = ref(false)

const form = ref({
  name: '',
  description: '',
  price: 0,
  sku: '',
  active: true
})

const submitting = ref(false)
const formSuccess = ref<string | null>(null)
const formError = ref<string | null>(null)

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await apiClient.get('/admin/stats')
    stats.value = res.data
  } catch (err: any) {
    console.error('Failed to load stats', err)
  } finally {
    statsLoading.value = false
  }
}

async function createProduct() {
  submitting.value = true
  formSuccess.value = null
  formError.value = null
  try {
    await apiClient.post('/products', form.value)
    formSuccess.value = `Successfully created product "${form.value.name}" with SKU "${form.value.sku}"!`
    form.value = { name: '', description: '', price: 0, sku: '', active: true }
  } catch (err: any) {
    formError.value = err.response?.data?.detail || 'Failed to create product'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (authStore.isAdmin) {
    loadStats()
  }
})
</script>

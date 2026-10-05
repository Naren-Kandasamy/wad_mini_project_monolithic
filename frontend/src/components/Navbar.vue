<template>
  <header style="background: white; border-bottom: 1px solid var(--border-color); padding: 1rem 0;">
    <div class="container" style="display: flex; justify-content: space-between; align-items: center;">
      <div style="display: flex; align-items: center; gap: 1.5rem;">
        <router-link to="/" style="font-weight: 700; font-size: 1.25rem; text-decoration: none; color: var(--text-main);">
          🛒 Shopping Cart
        </router-link>
        <nav style="display: flex; gap: 1rem;">
          <router-link to="/" class="nav-link">Catalog</router-link>
          <router-link to="/cart" class="nav-link">
            Cart
            <span v-if="cartStore.itemCount > 0" class="badge badge-success" style="margin-left: 0.25rem;">
              {{ cartStore.itemCount }}
            </span>
          </router-link>
          <router-link to="/orders" class="nav-link">My Orders</router-link>
          <router-link v-if="authStore.isAdmin" to="/admin" class="nav-link">Admin</router-link>
        </nav>
      </div>

      <div style="display: flex; align-items: center; gap: 0.75rem;">
        <template v-if="authStore.isAuthenticated">
          <span style="font-size: 0.875rem; color: var(--text-muted);">
            User: <strong>{{ authStore.currentUserId }}</strong>
            <span v-if="authStore.isAdmin" class="badge badge-admin" style="margin-left: 0.25rem;">ADMIN</span>
          </span>
          <button @click="authStore.logout" class="btn btn-outline" style="font-size: 0.75rem; padding: 0.25rem 0.5rem;">
            Logout
          </button>
        </template>
        <template v-else>
          <button @click="authStore.loginAsUser('user1')" class="btn btn-outline" style="font-size: 0.75rem;">
            Login as User
          </button>
          <button @click="authStore.loginAsAdmin('admin1')" class="btn btn-outline" style="font-size: 0.75rem;">
            Login as Admin
          </button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import { useCartStore } from '../stores/cart'

const authStore = useAuthStore()
const cartStore = useCartStore()
</script>

<style scoped>
.nav-link {
  text-decoration: none;
  color: var(--text-muted);
  font-weight: 500;
  font-size: 0.95rem;
  transition: color 0.15s;
}
.nav-link:hover,
.nav-link.router-link-active {
  color: var(--primary);
}
</style>

<template>
  <div style="min-height: 100vh; display: flex; flex-direction: column;">
    <Navbar />
    <main style="flex: 1;">
      <router-view />
    </main>
    <footer style="border-top: 1px solid var(--border-color); background: white; padding: 1.5rem 0; text-align: center; color: var(--text-muted); font-size: 0.85rem;">
      <div class="container">
        Shopping Cart Modular Monolith — Spring Boot 4.1.1 + Vue 3 SPA + MongoDB 7 + Keycloak 24
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import Navbar from './components/Navbar.vue'
</script>

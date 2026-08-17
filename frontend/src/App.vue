<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const inWorkspace = computed(() => route.name === "workspace");
</script>

<template>
  <div class="app-shell" :class="{ workspace: inWorkspace }">
    <header class="topbar">
      <RouterLink to="/" class="brand" aria-label="CodeAtlas 项目列表">
        <span class="brand-mark">CA</span>
        <span>
          <strong>CodeAtlas</strong>
          <small>Repository Intelligence</small>
        </span>
      </RouterLink>
      <div class="topbar-status">
        <span class="live-dot"></span>
        Read-only agent
      </div>
    </header>
    <main><RouterView /></main>
  </div>
</template>


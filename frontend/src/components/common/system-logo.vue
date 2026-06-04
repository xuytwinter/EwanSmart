<script lang="ts" setup>
import logoUrl from '@/assets/imgs/ewansmart-logo.png';

defineOptions({ name: 'SystemLogo' });
</script>

<template>
  <img :src="logoUrl" class="system-logo" alt="" aria-hidden="true" />
</template>

<style scoped>
.system-logo {
  display: block;
  width: 1em;
  height: 1em;
  object-fit: contain;
}
</style>

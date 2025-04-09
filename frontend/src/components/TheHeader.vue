<script setup lang="ts">
const router = useRouter()
function navTo(path: string) {
  router.push(`/auth/${path}`)
}

const store = useAuthStore()
</script>

<template>
  <header p-4 flex-bc gap-4>
    <nav text-xl inline-flex flex-1 gap-4 justify-end>
      <button icon-btn @click="toggleDark()">
        <div i-carbon-sun dark:i-carbon-moon />
      </button>

      <a
        i-carbon-logo-github icon-btn rel="noreferrer" href="https://github.com/SugaraguS/intelligent-pi.git"
        target="_blank" title="GitHub"
      />
    </nav>
    <a-divider type="vertical" />
    <UserAvatar v-if="store.isLogin" />
    <div v-else flex gap-4>
      <a-button type="primary" @click="navTo('sign-in')">
        登录
      </a-button>
      <a-button @click="navTo('sign-up')">
        注册
      </a-button>
    </div>
  </header>
</template>

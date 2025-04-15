<route lang="yaml">
  name: sign-in
</route>

<script setup lang="ts">
import type { Rule } from 'ant-design-vue/es/form'

interface LoginForm {
  username: string
  password: string
}

const loading = ref(false)

const form = ref<LoginForm>({
  username: 'test',
  password: '123',
})

const rules: Record<string, Rule[]> = {
  username: [{ required: true, message: '请输入您的用户名' }],
  password: [{ required: true, message: '请输入您的密码' }],
  passwordAgain: [{ required: true, message: '请再次输入您的密码' }],
}

const store = useAuthStore()

async function onFinish(form: LoginForm) {
  loading.value = true
  await store.signIn(form.username, form.password)
  loading.value = false
}

const router = useRouter()
function nav2SignUp() {
  router.push('signUp')
}
</script>

<template>
  <div bg-white flex-cc flex-col gap-8 h-full w-full dark:bg-black>
    <span text-7>登录 派聪明</span>
    <div class="p-8 b-1 b-white/6 rd-4 b-solid bg-white/10 shadow">
      <a-form
        size="large" :model="form" :rules="rules" :label-col="{ style: { width: 0 } }" autocomplete="off"
        class="w-350px" @finish="onFinish"
      >
        <a-form-item label="username" name="username" :rules="[{ required: true, message: '请输入您的用户名' }]">
          <a-input v-model:value="form.username" placeholder="请输入您的用户名">
            <template #prefix>
              <div i-ant-design-user-outlined color="#999" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item label="password" name="password">
          <a-input v-model:value="form.password" placeholder="请输入您的密码">
            <template #prefix>
              <div i-ant-design-lock-outlined color="#999" />
            </template>
          </a-input>
        </a-form-item>

        <a-button type="primary" html-type="submit" mt-4 block :loading="loading">
          登录
        </a-button>
        <a-button type="text" mt-4 block @click="nav2SignUp">
          注册账号
        </a-button>
      </a-form>
    </div>
    <span text-3 text-black mt-2 dark:text-white>
      注册即代表已阅读并同意我们的
      <a-button type="link" size="small">用户协议</a-button>
      和
      <a-button type="link" size="small">隐私政策</a-button>
    </span>
  </div>
</template>

<style scoped lang="scss">
.shadow {
  box-shadow:
    0 0 1px rgba(0, 0, 0, 0.2),
    0 0 4px rgba(0, 0, 0, 0.02),
    0 12px 36px rgba(0, 0, 0, 0.06);
}
</style>

<script setup lang="ts">
import { ref } from 'vue'
import { useGameStore } from '../stores/game'

const store = useGameStore()
const email = ref('player_a@example.com')
const password = ref('Test123456!')
const error = ref('')
const loading = ref(false)

async function doLogin() {
  error.value = ''
  loading.value = true
  try {
    await store.login(email.value, password.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

function quickLogin(target: string) {
  email.value = target
  password.value = 'Test123456!'
  doLogin()
}
</script>

<template>
  <div class="card login-card">
    <h2>1v1 连连看 · 在线对战</h2>
    <p class="muted">两个账号同时登录，匹配后进入同一棋盘实时对战</p>

    <label>邮箱</label>
    <input v-model="email" placeholder="邮箱" autocomplete="username" />
    <label style="display:block;margin-top:12px">密码</label>
    <input v-model="password" type="password" placeholder="密码" autocomplete="current-password" />

    <div class="error-text">{{ error }}</div>

    <button style="width:100%" :disabled="loading" @click="doLogin">
      {{ loading ? '登录中…' : '登录' }}
    </button>

    <div class="divider">快速登录</div>
    <div class="row" style="margin-top:12px">
      <button class="secondary" style="flex:1" @click="quickLogin('player_a@example.com')">Player A</button>
      <button class="secondary" style="flex:1" @click="quickLogin('player_b@example.com')">Player B</button>
    </div>
  </div>
</template>

<style scoped>
.login-card {
  max-width: 400px;
  margin: 60px auto 0;
}
label {
  font-size: 0.9rem;
  color: var(--muted);
}
.divider {
  margin: 20px 0 0;
  text-align: center;
  color: var(--muted);
  font-size: 0.85rem;
}
</style>
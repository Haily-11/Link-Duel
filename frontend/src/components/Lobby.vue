<script setup lang="ts">
import { onMounted } from 'vue'
import { useGameStore } from '../stores/game'

const store = useGameStore()

onMounted(() => {
  store.refreshLeaderboard()
})

function emailLabel(email: string) {
  return email.replace('@example.com', '')
}
</script>

<template>
  <div class="card lobby">
    <div class="row spread">
      <div>
        <h2>大厅</h2>
        <p class="muted">当前账号：{{ store.email }}
          <span v-if="store.connected" style="color:var(--ok)">● 已连接</span>
          <span v-else style="color:var(--danger)">● 未连接</span>
        </p>
      </div>
      <button class="secondary" @click="store.reset()">退出登录</button>
    </div>

    <div class="match-zone">
      <button class="match-btn" :disabled="store.isMatching" @click="store.startMatch()">
        {{ store.isMatching ? '正在匹配对手…' : '开始 1v1 匹配' }}
      </button>
      <p v-if="store.isMatching" class="muted">请让另一位玩家在另一个窗口登录并点击匹配</p>
    </div>

    <div class="leaderboard">
      <h3>🏆 排行榜</h3>
      <table v-if="store.leaderboard.length">
        <thead>
          <tr><th>#</th><th>玩家</th><th>胜场</th><th>积分</th></tr>
        </thead>
        <tbody>
          <tr v-for="entry in store.leaderboard" :key="entry.userId"
              :class="{ me: entry.userId === store.userId }">
            <td>{{ entry.rank }}</td>
            <td>{{ emailLabel(entry.email) }}</td>
            <td>{{ entry.wins }}</td>
            <td>{{ entry.points }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无战绩</p>
    </div>
  </div>
</template>

<style scoped>
.lobby {
  max-width: 520px;
  margin: 40px auto 0;
}
.match-zone {
  text-align: center;
  padding: 24px 0;
}
.match-btn {
  font-size: 1.1rem;
  padding: 14px 32px;
}
.leaderboard {
  margin-top: 16px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
}
th, td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
}
th {
  color: var(--muted);
  font-weight: 600;
}
tr.me {
  color: var(--accent-2);
  font-weight: 600;
}
</style>
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useGameStore } from '../stores/game'

const store = useGameStore()

const title = computed(() => {
  const g = store.gameOver
  if (!g) return ''
  if (g.winnerId === null) return '平局'
  return g.youWon ? '🎉 你赢了！' : '你输了'
})

const reasonText: Record<string, string> = {
  CLEAR: '棋盘清空',
  SURRENDER: '对方认输',
  DISCONNECT: '对方断线'
}

function emailLabel(email: string) {
  return email.replace('@example.com', '')
}

onMounted(() => {
  store.refreshLeaderboard()
})
</script>

<template>
  <div class="card result" v-if="store.gameOver">
    <h2>{{ title }}</h2>
    <p class="muted">
      比分 {{ store.gameOver.scoreA }} : {{ store.gameOver.scoreB }}
      · {{ reasonText[store.gameOver.endReason] || store.gameOver.endReason }}
    </p>

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

    <div class="row" style="margin-top:20px;justify-content:center">
      <button @click="store.clearGameOver()">再来一局</button>
      <button class="secondary" @click="store.reset()">退出登录</button>
    </div>
  </div>
</template>

<style scoped>
.result {
  max-width: 520px;
  margin: 40px auto 0;
  text-align: center;
}
.leaderboard {
  margin-top: 20px;
  text-align: left;
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
}
tr.me {
  color: var(--accent-2);
  font-weight: 600;
}
</style>
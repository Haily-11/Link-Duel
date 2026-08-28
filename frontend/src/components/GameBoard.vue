<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useGameStore } from '../stores/game'

const store = useGameStore()

const EMOJI_MAP: Record<number, string> = {
  1: '🍎', 2: '🍌', 3: '🍇', 4: '🍉',
  5: '🍓', 6: '🍒', 7: '🍍', 8: '🥑'
}

// 后端棋盘为 10x10，四周一圈 0 边框，可玩区域为内部 8x8
const cells = computed(() => {
  const g = store.room?.grid
  if (!g) return []
  const list: { r: number; c: number; value: number }[] = []
  for (let r = 1; r <= 8; r++) {
    for (let c = 1; c <= 8; c++) {
      list.push({ r, c, value: g[r]?.[c] ?? 0 })
    }
  }
  return list
})

const myScore = computed(() =>
  store.room?.youAre === 'A' ? store.room?.scoreA : store.room?.scoreB)
const oppScore = computed(() =>
  store.room?.youAre === 'A' ? store.room?.scoreB : store.room?.scoreA)

const selected = ref<[number, number] | null>(null)

watch(() => store.lastReject, (v) => {
  if (v) {
    setTimeout(() => store.clearReject(), 2500)
  }
})

function emoji(v: number) {
  return EMOJI_MAP[v] || '❓'
}

function isSelected(r: number, c: number) {
  return !!selected.value && selected.value[0] === r && selected.value[1] === c
}

function isFlashed(r: number, c: number): '' | 'mine' | 'opp' {
  const e = store.eliminated
  if (!e) return ''
  const hit = (e.p1[0] === r && e.p1[1] === c) || (e.p2[0] === r && e.p2[1] === c)
  if (!hit) return ''
  return e.byMe ? 'mine' : 'opp'
}

function onClick(r: number, c: number) {
  const g = store.room?.grid
  if (!g || g[r][c] === 0) return
  store.clearReject()
  if (!selected.value) {
    selected.value = [r, c]
    return
  }
  const [r1, c1] = selected.value
  selected.value = null
  if (r1 === r && c1 === c) return
  store.tryEliminate([r1, c1], [r, c])
}
</script>

<template>
  <div class="game" v-if="store.room">
    <div class="topbar">
      <div class="player me">
        <div class="label">你 ({{ store.room.youAre }})</div>
        <div class="score">{{ myScore }}</div>
      </div>
      <div class="vs">VS</div>
      <div class="player opp">
        <div class="label">
          对手 ({{ store.room.youAre === 'A' ? 'B' : 'A' }})
          <span v-if="!store.opponentOnline" class="offline">已断线</span>
        </div>
        <div class="score">{{ oppScore }}</div>
      </div>
    </div>

    <div class="board" :style="{ gridTemplateColumns: 'repeat(8, 1fr)' }">
      <div
        v-for="cell in cells"
        :key="`${cell.r}-${cell.c}`"
        class="cell"
        :class="{
          selected: isSelected(cell.r, cell.c),
          'flash-mine': isFlashed(cell.r, cell.c) === 'mine',
          'flash-opp': isFlashed(cell.r, cell.c) === 'opp',
          empty: cell.value === 0
        }"
        @click="onClick(cell.r, cell.c)"
      >
        <span v-if="cell.value !== 0">{{ emoji(cell.value) }}</span>
      </div>
    </div>

    <div class="footer">
      <button class="danger" @click="store.surrender()">认输</button>
      <span class="muted">选两个相同图案，路径不超过两次转弯即可消除</span>
    </div>

    <transition name="fade">
      <div v-if="store.lastReject" class="toast">{{ store.lastReject }}</div>
    </transition>
  </div>
</template>

<style scoped>
.game {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding-top: 8px;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 32px;
}
.player {
  text-align: center;
  min-width: 120px;
}
.player .label {
  color: var(--muted);
  font-size: 0.85rem;
}
.player .score {
  font-size: 2.2rem;
  font-weight: 700;
}
.me .score {
  color: var(--accent-2);
}
.vs {
  color: var(--muted);
  font-weight: 700;
}
.offline {
  color: var(--danger);
  font-size: 0.75rem;
}
.board {
  display: grid;
  gap: 6px;
  padding: 12px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  width: min(90vw, 480px);
}
.cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: clamp(18px, 5vw, 30px);
  background: var(--panel-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  transition: background 0.1s;
}
.cell:hover {
  background: #334155;
}
.cell.selected {
  border: 2px solid var(--accent);
  background: #1d4ed8;
}
.cell.empty {
  background: transparent;
  border: 1px dashed var(--border);
  cursor: default;
}
.cell.flash-mine {
  animation: flash 0.9s ease;
  background: var(--accent-2);
}
.cell.flash-opp {
  animation: flash 0.9s ease;
  background: #f59e0b;
}
@keyframes flash {
  0% { opacity: 1; transform: scale(1.15); }
  40% { opacity: 0.3; }
  100% { opacity: 1; transform: scale(1); }
}
.footer {
  display: flex;
  align-items: center;
  gap: 16px;
}
.toast {
  position: fixed;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--danger);
  color: #fff;
  padding: 10px 18px;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
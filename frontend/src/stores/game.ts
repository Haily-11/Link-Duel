import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as apiLogin, fetchLeaderboard, type RankingEntry } from '../api'

export interface Room {
  roomId: string
  playerA: number
  playerB: number
  youAre: 'A' | 'B'
  grid: number[][]
  scoreA: number
  scoreB: number
}

export interface Eliminated {
  p1: [number, number]
  p2: [number, number]
  byMe: boolean
}

export interface GameOver {
  winnerId: number | null
  scoreA: number
  scoreB: number
  endReason: string
  youWon: boolean
}

export const useGameStore = defineStore('game', () => {
  const token = ref('')
  const userId = ref<number | null>(null)
  const email = ref('')
  const connected = ref(false)
  const isMatching = ref(false)
  const room = ref<Room | null>(null)
  const eliminated = ref<Eliminated | null>(null)
  const lastReject = ref('')
  const gameOver = ref<GameOver | null>(null)
  const opponentOnline = ref(true)
  const leaderboard = ref<RankingEntry[]>([])

  let socket: WebSocket | null = null
  let elimTimer: ReturnType<typeof setTimeout> | null = null

  function connectSocket() {
    if (!token.value) return
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    socket = new WebSocket(`${proto}://${location.host}/ws/game?token=${token.value}`)
    socket.onopen = () => {
      connected.value = true
    }
    socket.onclose = () => {
      connected.value = false
    }
    socket.onmessage = (ev) => {
      try {
        handle(JSON.parse(ev.data))
      } catch {
        /* 忽略非法消息 */
      }
    }
  }

  function handle(msg: any) {
    switch (msg.type) {
      case 'MATCH_WAITING':
        isMatching.value = true
        break
      case 'MATCH_SUCCESS':
      case 'RECONNECT_STATE':
        room.value = {
          roomId: msg.roomId,
          playerA: msg.playerA,
          playerB: msg.playerB,
          youAre: msg.youAre,
          grid: msg.grid,
          scoreA: msg.scoreA,
          scoreB: msg.scoreB
        }
        isMatching.value = false
        gameOver.value = null
        opponentOnline.value = true
        break
      case 'ELIMINATE_SYNC':
        if (room.value) {
          room.value.grid = msg.grid
          room.value.scoreA = msg.scoreA
          room.value.scoreB = msg.scoreB
        }
        eliminated.value = {
          p1: msg.p1 as [number, number],
          p2: msg.p2 as [number, number],
          byMe: msg.operatorId === userId.value
        }
        if (elimTimer) clearTimeout(elimTimer)
        elimTimer = setTimeout(() => (eliminated.value = null), 900)
        break
      case 'ELIMINATE_REJECT':
        lastReject.value = msg.reason || '操作不合法'
        break
      case 'GAME_OVER':
        gameOver.value = {
          winnerId: msg.winnerId,
          scoreA: msg.scoreA,
          scoreB: msg.scoreB,
          endReason: msg.endReason,
          youWon: msg.youWon
        }
        room.value = null
        isMatching.value = false
        break
      case 'OPPONENT_DISCONNECTED':
        opponentOnline.value = false
        break
      case 'OPPONENT_RECONNECTED':
        opponentOnline.value = true
        break
      case 'ERROR':
        lastReject.value = msg.message || ''
        break
      case 'PONG':
        break
    }
  }

  function send(type: string, payload: Record<string, unknown> = {}) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type, ...payload }))
    }
  }

  async function login(emailInput: string, password: string) {
    const res = await apiLogin(emailInput, password)
    token.value = res.token
    userId.value = res.userId
    email.value = res.email
    connectSocket()
  }

  function startMatch() {
    isMatching.value = true
    send('MATCH_START')
  }

  function tryEliminate(p1: [number, number], p2: [number, number]) {
    if (!room.value) return
    send('TRY_ELIMINATE', { roomId: room.value.roomId, p1, p2 })
  }

  function surrender() {
    send('SURRENDER')
  }

  async function refreshLeaderboard() {
    try {
      leaderboard.value = await fetchLeaderboard()
    } catch {
      /* 忽略 */
    }
  }

  function clearReject() {
    lastReject.value = ''
  }

  function clearGameOver() {
    gameOver.value = null
  }

  function reset() {
    socket?.close()
    socket = null
    token.value = ''
    userId.value = null
    email.value = ''
    room.value = null
    isMatching.value = false
    gameOver.value = null
    eliminated.value = null
    connected.value = false
  }

  return {
    token, userId, email, connected, isMatching, room,
    eliminated, lastReject, gameOver, opponentOnline, leaderboard,
    login, connectSocket, startMatch, tryEliminate, surrender,
    refreshLeaderboard, clearReject, clearGameOver, reset
  }
})
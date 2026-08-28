<script setup lang="ts">
import { useGameStore } from './stores/game'
import Login from './components/Login.vue'
import Lobby from './components/Lobby.vue'
import GameBoard from './components/GameBoard.vue'
import Result from './components/Result.vue'

const store = useGameStore()
</script>

<template>
  <Login v-if="!store.userId" />
  <GameBoard v-else-if="store.room" />
  <Result v-else-if="store.gameOver" />
  <Lobby v-else />
</template>
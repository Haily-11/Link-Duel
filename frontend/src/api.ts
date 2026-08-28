import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 8000 })

export interface LoginResult {
  userId: number
  email: string
  token: string
}

export interface RankingEntry {
  rank: number
  userId: number
  email: string
  wins: number
  points: number
}

export async function login(email: string, password: string): Promise<LoginResult> {
  const { data } = await http.post('/auth/login', { email, password })
  if (!data.success) {
    throw new Error(data.message || '登录失败')
  }
  return data.data as LoginResult
}

export async function fetchLeaderboard(): Promise<RankingEntry[]> {
  const { data } = await http.get('/ranking/top')
  return (data.data ?? []) as RankingEntry[]
}
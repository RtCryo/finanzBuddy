import { api } from './client'
import type { DashboardMetrics } from '../types/dashboard'

export async function getDashboard() {
  const response = await api.get<DashboardMetrics>('/dashboard')
  return response.data
}

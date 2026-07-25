import { get } from './client'

export function obterDashboard() {
  return get('/pedidos/dashboard')
}

import { get } from './client'

export function obterRota(viagemId) {
  return get(`/entregas/rota/${viagemId}`)
}

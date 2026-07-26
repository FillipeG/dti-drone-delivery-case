import { get, post } from './client'

export function obterStatusSimulacao() {
  return get('/simulacao/status')
}

export function avancarTempo(minutos) {
  return post(`/simulacao/avancar?minutos=${minutos}`)
}

export function definirAutomatica(ativo) {
  return post(`/simulacao/automatica?ativo=${ativo}`)
}

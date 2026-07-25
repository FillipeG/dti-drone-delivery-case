import { get, post, put } from './client'

export function listarPedidos() {
  return get('/pedidos')
}

export function criarPedido({ coordenadaX, coordenadaY, peso, prioridade }) {
  return post('/pedidos', { coordenadaX, coordenadaY, peso, prioridade })
}

export function concluirPedido(id) {
  return put(`/pedidos/${id}/concluir`)
}

export function rastrearPedido(id) {
  return get(`/pedidos/${id}/rastreio`)
}

export function processarFila() {
  return post('/pedidos/processar-fila')
}

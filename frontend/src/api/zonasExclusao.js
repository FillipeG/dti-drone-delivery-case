import { get, post, del } from './client'

export function listarZonas() {
  return get('/zonas-exclusao')
}

export function cadastrarZona({ nome, coordenadaX, coordenadaY, raioKm }) {
  return post('/zonas-exclusao', { nome, coordenadaX, coordenadaY, raioKm })
}

export function removerZona(id) {
  return del(`/zonas-exclusao/${id}`)
}

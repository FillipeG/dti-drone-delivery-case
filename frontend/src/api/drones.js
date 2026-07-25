import { get, post, put } from './client'

export function listarStatusDrones() {
  return get('/drones/status')
}

export function cadastrarDrone({ capacidadeMaximaPeso, autonomiaMaximaKm }) {
  return post('/drones', { capacidadeMaximaPeso, autonomiaMaximaKm })
}

export function recarregarDrone(id) {
  return put(`/drones/${id}/recarregar`)
}

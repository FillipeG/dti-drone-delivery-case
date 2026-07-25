const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  if (!response.ok) {
    const corpo = await response.json().catch(() => null)
    const mensagemDosCampos = corpo?.erros && Object.values(corpo.erros).join(', ')
    throw new Error(mensagemDosCampos || corpo?.mensagem || `Erro ${response.status} ao chamar ${path}`)
  }

  if (response.status === 204) {
    return null
  }

  return response.json()
}

export function get(path) {
  return request(path)
}

export function post(path, body) {
  return request(path, { method: 'POST', body: JSON.stringify(body) })
}

export function put(path, body) {
  return request(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined })
}

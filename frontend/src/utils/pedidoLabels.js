const PRIORIDADE = {
  ALTA: { label: 'alta', tone: 'danger' },
  MEDIA: { label: 'média', tone: 'warning' },
  BAIXA: { label: 'baixa', tone: 'neutral' },
}

const STATUS_PEDIDO = {
  PENDENTE: { label: 'pendente', tone: 'neutral' },
  EM_TRANSPORTE: { label: 'em transporte', tone: 'info' },
  ENTREGUE: { label: 'entregue', tone: 'success' },
  CANCELADO: { label: 'cancelado', tone: 'danger' },
}

export function prioridadeLabel(prioridade) {
  return PRIORIDADE[prioridade]?.label ?? prioridade
}

export function prioridadeTone(prioridade) {
  return PRIORIDADE[prioridade]?.tone ?? 'neutral'
}

export function statusPedidoLabel(status) {
  return STATUS_PEDIDO[status]?.label ?? status
}

export function statusPedidoTone(status) {
  return STATUS_PEDIDO[status]?.tone ?? 'neutral'
}

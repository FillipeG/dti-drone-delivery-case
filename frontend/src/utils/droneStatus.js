const STATUS = {
  IDLE: { label: 'idle', tone: 'neutral' },
  CARREGANDO: { label: 'carregando', tone: 'neutral' },
  EM_VOO: { label: 'em voo', tone: 'warning' },
  ENTREGANDO: { label: 'entregando', tone: 'success' },
  RETORNANDO: { label: 'retornando', tone: 'neutral' },
}

export function droneStatusLabel(status) {
  return STATUS[status]?.label ?? status
}

export function droneStatusTone(status) {
  return STATUS[status]?.tone ?? 'neutral'
}

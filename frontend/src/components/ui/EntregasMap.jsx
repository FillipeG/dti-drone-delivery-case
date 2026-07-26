import { useState } from 'react'
import Modal from './Modal'
import Badge from './Badge'
import { prioridadeLabel, prioridadeTone, statusPedidoLabel, statusPedidoTone } from '../../utils/pedidoLabels'
import './EntregasMap.css'

const COR_POR_STATUS = {
  PENDENTE: 'var(--color-text-muted)',
  EM_TRANSPORTE: 'var(--dti-cyan)',
  ENTREGUE: 'var(--color-success-text)',
  CANCELADO: 'var(--color-danger-text)',
}

function posicao(valor, maiorCoordenada) {
  // mapeia a coordenada pro raio de 45% em torno do centro do mapa
  return 50 + (valor / maiorCoordenada) * 45
}

function EntregasMap({ pedidos }) {
  const [pedidoSelecionado, setPedidoSelecionado] = useState(null)

  const maiorCoordenada = pedidos.reduce((maior, p) => {
    return Math.max(maior, Math.abs(p.coordenadaX), Math.abs(p.coordenadaY))
  }, 1)

  return (
    <div className="entregas-map">
      <div className="entregas-map__marcador entregas-map__marcador--base" style={{ left: '50%', top: '50%' }}>
        <span className="entregas-map__legenda">base</span>
      </div>

      {pedidos.map((pedido) => (
        <button
          key={pedido.id}
          type="button"
          className="entregas-map__marcador entregas-map__marcador--pedido"
          style={{
            left: `${posicao(pedido.coordenadaX, maiorCoordenada)}%`,
            top: `${100 - posicao(pedido.coordenadaY, maiorCoordenada)}%`,
            background: COR_POR_STATUS[pedido.status] ?? COR_POR_STATUS.PENDENTE,
          }}
          title={`(${pedido.coordenadaX}, ${pedido.coordenadaY}) · ${pedido.status}`}
          onClick={() => setPedidoSelecionado(pedido)}
        />
      ))}

      {pedidoSelecionado && (
        <Modal title={`Pedido ${pedidoSelecionado.id.slice(0, 8)}`} onClose={() => setPedidoSelecionado(null)}>
          <div className="entregas-map__detalhe">
            <p>
              Posição: ({pedidoSelecionado.coordenadaX}, {pedidoSelecionado.coordenadaY})
            </p>
            <p>Peso: {pedidoSelecionado.peso} kg</p>
            <p className="entregas-map__detalhe-badges">
              <Badge tone={prioridadeTone(pedidoSelecionado.prioridade)}>
                {prioridadeLabel(pedidoSelecionado.prioridade)}
              </Badge>
              <Badge tone={statusPedidoTone(pedidoSelecionado.status)}>
                {statusPedidoLabel(pedidoSelecionado.status)}
              </Badge>
            </p>
            {pedidoSelecionado.droneAlocado && (
              <p>Drone alocado: {pedidoSelecionado.droneAlocado.id.slice(0, 8)}</p>
            )}
            {pedidoSelecionado.tempoEstimadoMinutos != null && (
              <p>Tempo estimado: {pedidoSelecionado.tempoEstimadoMinutos} min</p>
            )}
          </div>
        </Modal>
      )}
    </div>
  )
}

export default EntregasMap

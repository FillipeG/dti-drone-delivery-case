import { useEffect, useRef, useState } from 'react'
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

function EntregasMap({ pedidos, zonas = [] }) {
  const containerRef = useRef(null)
  const [tamanho, setTamanho] = useState({ largura: 0, altura: 0 })
  const [pedidoSelecionado, setPedidoSelecionado] = useState(null)
  const [zonaSelecionada, setZonaSelecionada] = useState(null)

  useEffect(() => {
    function medir() {
      if (containerRef.current) {
        const { width, height } = containerRef.current.getBoundingClientRect()
        setTamanho({ largura: width, altura: height })
      }
    }

    medir()
    window.addEventListener('resize', medir)
    return () => window.removeEventListener('resize', medir)
  }, [])

  // maior distância do centro (considerando o raio das zonas) pra caber tudo no mapa
  const maiorCoordenada = [...pedidos, ...zonas].reduce((maior, item) => {
    const raio = item.raioKm ?? 0
    return Math.max(maior, Math.abs(item.coordenadaX) + raio, Math.abs(item.coordenadaY) + raio)
  }, 1)

  const raioVisivelPx = (Math.min(tamanho.largura, tamanho.altura) / 2) * 0.85
  const pxPorKm = tamanho.largura > 0 ? raioVisivelPx / maiorCoordenada : 0

  function posicaoPx(x, y) {
    return {
      left: tamanho.largura / 2 + x * pxPorKm,
      top: tamanho.altura / 2 - y * pxPorKm,
    }
  }

  const pronto = tamanho.largura > 0

  return (
    <div className="entregas-map" ref={containerRef}>
      {pronto && (
        <>
          {zonas.map((zona) => {
            const raioPx = zona.raioKm * pxPorKm
            return (
              <button
                key={zona.id}
                type="button"
                className="entregas-map__zona"
                style={{ ...posicaoPx(zona.coordenadaX, zona.coordenadaY), width: raioPx * 2, height: raioPx * 2 }}
                title={`${zona.nome} · raio ${zona.raioKm} km`}
                onClick={() => setZonaSelecionada(zona)}
              />
            )
          })}

          <div className="entregas-map__marcador entregas-map__marcador--base" style={posicaoPx(0, 0)}>
            <span className="entregas-map__legenda">base</span>
          </div>

          {pedidos.map((pedido) => (
            <button
              key={pedido.id}
              type="button"
              className="entregas-map__marcador entregas-map__marcador--pedido"
              style={{
                ...posicaoPx(pedido.coordenadaX, pedido.coordenadaY),
                background: COR_POR_STATUS[pedido.status] ?? COR_POR_STATUS.PENDENTE,
              }}
              title={`(${pedido.coordenadaX}, ${pedido.coordenadaY}) · ${pedido.status}`}
              onClick={() => setPedidoSelecionado(pedido)}
            />
          ))}
        </>
      )}

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

      {zonaSelecionada && (
        <Modal title={zonaSelecionada.nome} onClose={() => setZonaSelecionada(null)}>
          <div className="entregas-map__detalhe">
            <p>
              Centro: ({zonaSelecionada.coordenadaX}, {zonaSelecionada.coordenadaY})
            </p>
            <p>Raio: {zonaSelecionada.raioKm} km</p>
          </div>
        </Modal>
      )}
    </div>
  )
}

export default EntregasMap

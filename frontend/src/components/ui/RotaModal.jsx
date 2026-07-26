import { useEffect, useState } from 'react'
import Modal from './Modal'
import Badge from './Badge'
import { obterRota } from '../../api/entregas'
import { prioridadeLabel, prioridadeTone, statusPedidoLabel, statusPedidoTone } from '../../utils/pedidoLabels'
import './RotaModal.css'

function RotaModal({ viagemId, onClose }) {
  const [rota, setRota] = useState(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false

    obterRota(viagemId)
      .then((dados) => {
        if (!cancelado) setRota(dados)
      })
      .catch((err) => {
        if (!cancelado) setErro(err.message)
      })
      .finally(() => {
        if (!cancelado) setLoading(false)
      })

    return () => {
      cancelado = true
    }
  }, [viagemId])

  return (
    <Modal title="Rota da viagem" onClose={onClose} width={420}>
      {loading && <p>Carregando rota...</p>}
      {erro && <p className="rota-modal__erro">{erro}</p>}

      {rota && (
        <>
          <div className="rota-modal__resumo">
            <p>Drone: {rota.droneId.slice(0, 8)}</p>
            <p>
              {rota.totalParadas} parada(s) · {rota.pesoTotalKg} kg · {rota.distanciaTotalKm} km ·{' '}
              {rota.tempoEstimadoMinutos} min
            </p>
            <Badge tone={rota.concluida ? 'success' : 'info'}>
              {rota.concluida ? 'concluída' : 'em andamento'}
            </Badge>
          </div>

          <ol className="rota-modal__paradas">
            {rota.paradas.map((parada) => (
              <li key={parada.pedidoId}>
                <div className="rota-modal__parada-linha">
                  <span>
                    ({parada.coordenadaX}, {parada.coordenadaY}) · {parada.pesoKg} kg
                  </span>
                  <Badge tone={prioridadeTone(parada.prioridade)}>{prioridadeLabel(parada.prioridade)}</Badge>
                </div>
                <div className="rota-modal__parada-linha rota-modal__parada-linha--detalhe">
                  <span>
                    +{parada.distanciaDoPontoAnteriorKm} km · chega em {parada.tempoAcumuladoAteEntregaMinutos} min
                  </span>
                  <Badge tone={statusPedidoTone(parada.status)}>{statusPedidoLabel(parada.status)}</Badge>
                </div>
              </li>
            ))}
          </ol>

          <p className="rota-modal__retorno">Retorno à base: {rota.distanciaRetornoBaseKm} km</p>
        </>
      )}
    </Modal>
  )
}

export default RotaModal

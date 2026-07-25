import { useEffect, useState } from 'react'
import StatTile from '../components/ui/StatTile'
import { obterDashboard } from '../api/dashboard'
import './Dashboard.css'

function Dashboard() {
  const [dados, setDados] = useState(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false

    obterDashboard()
      .then((resposta) => {
        if (!cancelado) {
          setDados(resposta)
          setErro(null)
        }
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
  }, [])

  return (
    <div>
      <h1>Dashboard</h1>

      {loading && <p>Carregando indicadores...</p>}
      {erro && <p className="dashboard__erro">{erro}</p>}

      {dados && (
        <div className="dashboard__kpis">
          <StatTile label="entregas realizadas" value={dados.entregasRealizadas} />
          <StatTile label="tempo médio por entrega" value={`${dados.tempoMedioMinutos} min`} />
          <StatTile
            label="drone mais eficiente"
            value={dados.droneMaisEficiente ? dados.droneMaisEficiente.slice(0, 8) : '—'}
          />
          <StatTile label="pedidos na fila" value={dados.pedidosNaFila} tone="highlight" />
        </div>
      )}
    </div>
  )
}

export default Dashboard

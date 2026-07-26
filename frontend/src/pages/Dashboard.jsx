import { useEffect, useState } from 'react'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Badge from '../components/ui/Badge'
import StatTile from '../components/ui/StatTile'
import { obterDashboard } from '../api/dashboard'
import { obterStatusSimulacao, avancarTempo, definirAutomatica } from '../api/simulacao'
import './Dashboard.css'

function Dashboard() {
  const [dados, setDados] = useState(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  const [simulacao, setSimulacao] = useState(null)
  const [minutosInput, setMinutosInput] = useState('5')
  const [avancando, setAvancando] = useState(false)
  const [alternandoAutomatico, setAlternandoAutomatico] = useState(false)
  const [erroSimulacao, setErroSimulacao] = useState(null)

  function carregarDashboard() {
    return obterDashboard()
      .then((resposta) => {
        setDados(resposta)
        setErro(null)
      })
      .catch((err) => setErro(err.message))
  }

  useEffect(() => {
    let cancelado = false

    Promise.all([obterDashboard(), obterStatusSimulacao()])
      .then(([dadosDashboard, statusSimulacao]) => {
        if (!cancelado) {
          setDados(dadosDashboard)
          setSimulacao(statusSimulacao)
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

  useEffect(() => {
    if (!simulacao?.automatica) return undefined

    const intervalo = setInterval(() => {
      Promise.all([obterStatusSimulacao(), obterDashboard()])
        .then(([statusSimulacao, dadosDashboard]) => {
          setSimulacao(statusSimulacao)
          setDados(dadosDashboard)
        })
        .catch(() => {})
    }, 2000)

    return () => clearInterval(intervalo)
  }, [simulacao?.automatica])

  async function handleAvancar(event) {
    event.preventDefault()
    setErroSimulacao(null)
    setAvancando(true)

    try {
      const novoStatus = await avancarTempo(Number(minutosInput))
      setSimulacao(novoStatus)
      await carregarDashboard()
    } catch (err) {
      setErroSimulacao(err.message)
    } finally {
      setAvancando(false)
    }
  }

  async function handleAlternarAutomatico() {
    setErroSimulacao(null)
    setAlternandoAutomatico(true)

    try {
      const novoStatus = await definirAutomatica(!simulacao.automatica)
      setSimulacao(novoStatus)
    } catch (err) {
      setErroSimulacao(err.message)
    } finally {
      setAlternandoAutomatico(false)
    }
  }

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

      {simulacao && (
        <Card title="Simulação" className="dashboard__simulacao-card">
          <div className="dashboard__simulacao">
            <div className="dashboard__simulacao-info">
              <p>
                Relógio: <strong>{simulacao.relogioMinutos} min</strong> simulados
              </p>
              <p>
                {simulacao.dronesEmOperacao} drone(s) em operação ·{' '}
                <Badge tone={simulacao.automatica ? 'success' : 'neutral'}>
                  {simulacao.automatica ? 'automático ligado' : 'automático desligado'}
                </Badge>
              </p>
            </div>

            <form className="dashboard__simulacao-form" onSubmit={handleAvancar}>
              <input
                type="number"
                min="1"
                step="1"
                value={minutosInput}
                onChange={(e) => setMinutosInput(e.target.value)}
              />
              <Button type="submit" disabled={avancando}>
                {avancando ? 'Avançando...' : 'Avançar tempo (min)'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={alternandoAutomatico}
                onClick={handleAlternarAutomatico}
              >
                {simulacao.automatica ? 'Desativar automático' : 'Ativar automático'}
              </Button>
            </form>
          </div>

          {erroSimulacao && <p className="dashboard__erro">{erroSimulacao}</p>}
        </Card>
      )}
    </div>
  )
}

export default Dashboard

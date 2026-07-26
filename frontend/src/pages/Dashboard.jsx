import { useEffect, useState } from 'react'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Badge from '../components/ui/Badge'
import StatTile from '../components/ui/StatTile'
import EntregasMap from '../components/ui/EntregasMap'
import ZonasExclusaoModal from '../components/ui/ZonasExclusaoModal'
import { useToast } from '../components/ui/useToast'
import { obterDashboard } from '../api/dashboard'
import { obterStatusSimulacao, avancarTempo, definirAutomatica } from '../api/simulacao'
import { listarStatusDrones } from '../api/drones'
import { listarPedidos } from '../api/pedidos'
import { listarZonas } from '../api/zonasExclusao'
import { droneStatusLabel, droneStatusTone } from '../utils/droneStatus'
import './Dashboard.css'

function Dashboard() {
  const showToast = useToast()
  const [dados, setDados] = useState(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  const [pedidos, setPedidos] = useState([])
  const [zonas, setZonas] = useState([])
  const [drones, setDrones] = useState([])
  const [simulacao, setSimulacao] = useState(null)
  const [minutosInput, setMinutosInput] = useState('5')
  const [avancando, setAvancando] = useState(false)
  const [alternandoAutomatico, setAlternandoAutomatico] = useState(false)
  const [erroSimulacao, setErroSimulacao] = useState(null)
  const [modalZonasAberto, setModalZonasAberto] = useState(false)

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

    Promise.all([obterDashboard(), obterStatusSimulacao(), listarStatusDrones(), listarPedidos(), listarZonas()])
      .then(([dadosDashboard, statusSimulacao, statusDrones, listaPedidos, listaZonas]) => {
        if (!cancelado) {
          setDados(dadosDashboard)
          setSimulacao(statusSimulacao)
          setDrones(statusDrones)
          setPedidos(listaPedidos)
          setZonas(listaZonas)
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
      Promise.all([obterStatusSimulacao(), obterDashboard(), listarStatusDrones(), listarPedidos()])
        .then(([statusSimulacao, dadosDashboard, statusDrones, listaPedidos]) => {
          setSimulacao(statusSimulacao)
          setDados(dadosDashboard)
          setDrones(statusDrones)
          setPedidos(listaPedidos)
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

  function handleFecharModalZonas() {
    setModalZonasAberto(false)
    listarZonas()
      .then(setZonas)
      .catch(() => {})
  }

  async function handleAlternarAutomatico() {
    setErroSimulacao(null)
    setAlternandoAutomatico(true)

    try {
      const novoStatus = await definirAutomatica(!simulacao.automatica)
      setSimulacao(novoStatus)
      showToast(novoStatus.automatica ? 'Simulação automática ativada!' : 'Simulação automática desativada!')
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

      <div className="dashboard__mapa-frota">
        <Card
          title="Mapa das entregas"
          className="dashboard__mapa-card"
          action={
            <Button variant="secondary" onClick={() => setModalZonasAberto(true)}>
              Zonas de exclusão
            </Button>
          }
        >
          <EntregasMap pedidos={pedidos} zonas={zonas} />
        </Card>

        {drones.length > 0 && (
          <Card title="Status da frota" className="dashboard__frota-card">
            <div className="dashboard__frota">
              {drones.map((drone) => (
                <div key={drone.id} className="dashboard__frota-item">
                  <span>{drone.id.slice(0, 8)}</span>
                  <Badge tone={droneStatusTone(drone.status)}>{droneStatusLabel(drone.status)}</Badge>
                </div>
              ))}
            </div>
          </Card>
        )}
      </div>

      {modalZonasAberto && <ZonasExclusaoModal onClose={handleFecharModalZonas} />}
    </div>
  )
}

export default Dashboard

import { useEffect, useState } from 'react'
import Card from '../components/ui/Card'
import Badge from '../components/ui/Badge'
import Button from '../components/ui/Button'
import RotaModal from '../components/ui/RotaModal'
import { useToast } from '../components/ui/useToast'
import { listarPedidos, criarPedido, concluirPedido, rastrearPedido, processarFila } from '../api/pedidos'
import { prioridadeLabel, prioridadeTone, statusPedidoLabel, statusPedidoTone } from '../utils/pedidoLabels'
import './Pedidos.css'

const FORM_INICIAL = { coordenadaX: '', coordenadaY: '', peso: '', prioridade: 'MEDIA' }

function Pedidos() {
  const showToast = useToast()
  const [pedidos, setPedidos] = useState([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  const [form, setForm] = useState(FORM_INICIAL)
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState(null)

  const [concluindoId, setConcluindoId] = useState(null)
  const [rastreio, setRastreio] = useState(null)
  const [rastreioCarregandoId, setRastreioCarregandoId] = useState(null)
  const [reprocessando, setReprocessando] = useState(false)
  const [viagemSelecionada, setViagemSelecionada] = useState(null)

  async function carregarPedidos() {
    try {
      const dados = await listarPedidos()
      setPedidos(dados)
      setErro(null)
    } catch (err) {
      setErro(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelado = false

    listarPedidos()
      .then((dados) => {
        if (!cancelado) {
          setPedidos(dados)
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

  async function handleSubmit(event) {
    event.preventDefault()
    setErroForm(null)
    setSalvando(true)

    try {
      await criarPedido({
        coordenadaX: Number(form.coordenadaX),
        coordenadaY: Number(form.coordenadaY),
        peso: Number(form.peso),
        prioridade: form.prioridade,
      })
      setForm(FORM_INICIAL)
      await carregarPedidos()
      showToast('Pedido criado!')
    } catch (err) {
      setErroForm(err.message)
    } finally {
      setSalvando(false)
    }
  }

  async function handleConcluir(id) {
    setConcluindoId(id)
    try {
      await concluirPedido(id)
      await carregarPedidos()
      showToast('Pedido concluído!')
    } catch (err) {
      setErro(err.message)
    } finally {
      setConcluindoId(null)
    }
  }

  async function handleReprocessarFila() {
    setReprocessando(true)
    try {
      await processarFila()
      await carregarPedidos()
      showToast('Fila reprocessada!')
    } catch (err) {
      setErro(err.message)
    } finally {
      setReprocessando(false)
    }
  }

  async function handleRastrear(id) {
    if (rastreio?.pedidoId === id) {
      setRastreio(null)
      return
    }

    setRastreioCarregandoId(id)
    try {
      const dados = await rastrearPedido(id)
      setRastreio(dados)
    } catch (err) {
      setErro(err.message)
    } finally {
      setRastreioCarregandoId(null)
    }
  }

  return (
    <div>
      <h1>Pedidos</h1>

      <Card title="Novo pedido" className="pedidos-form-card">
        <form className="pedidos-form" onSubmit={handleSubmit}>
          <label className="pedidos-form__field">
            <span>Coordenada X</span>
            <input
              type="number"
              step="0.1"
              required
              value={form.coordenadaX}
              onChange={(e) => setForm({ ...form, coordenadaX: e.target.value })}
            />
          </label>

          <label className="pedidos-form__field">
            <span>Coordenada Y</span>
            <input
              type="number"
              step="0.1"
              required
              value={form.coordenadaY}
              onChange={(e) => setForm({ ...form, coordenadaY: e.target.value })}
            />
          </label>

          <label className="pedidos-form__field">
            <span>Peso (kg)</span>
            <input
              type="number"
              min="0.1"
              step="0.1"
              required
              value={form.peso}
              onChange={(e) => setForm({ ...form, peso: e.target.value })}
            />
          </label>

          <label className="pedidos-form__field">
            <span>Prioridade</span>
            <select
              value={form.prioridade}
              onChange={(e) => setForm({ ...form, prioridade: e.target.value })}
            >
              <option value="ALTA">alta</option>
              <option value="MEDIA">média</option>
              <option value="BAIXA">baixa</option>
            </select>
          </label>

          <Button type="submit" disabled={salvando}>
            {salvando ? 'Criando...' : 'Criar pedido'}
          </Button>
        </form>

        {erroForm && <p className="pedidos-form__erro">{erroForm}</p>}
      </Card>

      {rastreio && (
        <Card className="pedidos-rastreio-card">
          <p className="pedidos-rastreio__mensagem">{rastreio.mensagem}</p>
          {rastreio.distanciaRestanteKm != null && (
            <p className="pedidos-rastreio__detalhe">
              {rastreio.distanciaRestanteKm} km restantes · aprox. {rastreio.tempoRestanteMinutos} min
            </p>
          )}
        </Card>
      )}

      <Card
        title="Fila de pedidos"
        className="pedidos-list-card"
        action={
          <Button variant="secondary" disabled={reprocessando} onClick={handleReprocessarFila}>
            {reprocessando ? 'Reprocessando...' : 'Reprocessar fila'}
          </Button>
        }
      >
        {loading && <p>Carregando pedidos...</p>}
        {erro && <p className="pedidos-form__erro">{erro}</p>}
        {!loading && !erro && pedidos.length === 0 && (
          <p>Nenhum pedido por aqui ainda — crie o primeiro pedido acima pra começar a operação.</p>
        )}

        {!loading && pedidos.length > 0 && (
          <div className="pedidos-table-wrap">
          <table className="pedidos-table">
            <thead>
              <tr>
                <th>Pedido</th>
                <th>Cliente (X, Y)</th>
                <th>Peso</th>
                <th>Prioridade</th>
                <th>Status</th>
                <th>Drone</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pedidos.map((pedido) => (
                <tr key={pedido.id}>
                  <td>{pedido.id.slice(0, 8)}</td>
                  <td>({pedido.coordenadaX}, {pedido.coordenadaY})</td>
                  <td>{pedido.peso} kg</td>
                  <td>
                    <Badge tone={prioridadeTone(pedido.prioridade)}>{prioridadeLabel(pedido.prioridade)}</Badge>
                  </td>
                  <td>
                    <Badge tone={statusPedidoTone(pedido.status)}>{statusPedidoLabel(pedido.status)}</Badge>
                  </td>
                  <td>{pedido.droneAlocado ? pedido.droneAlocado.id.slice(0, 8) : '—'}</td>
                  <td className="pedidos-table__acoes">
                    <Button
                      variant="ghost"
                      disabled={rastreioCarregandoId === pedido.id}
                      onClick={() => handleRastrear(pedido.id)}
                    >
                      {rastreioCarregandoId === pedido.id ? '...' : 'Rastrear'}
                    </Button>
                    {pedido.viagemId && (
                      <Button variant="ghost" onClick={() => setViagemSelecionada(pedido.viagemId)}>
                        Ver rota
                      </Button>
                    )}
                    {pedido.status === 'EM_TRANSPORTE' && (
                      <Button
                        variant="secondary"
                        disabled={concluindoId === pedido.id}
                        onClick={() => handleConcluir(pedido.id)}
                      >
                        {concluindoId === pedido.id ? 'Concluindo...' : 'Concluir'}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </Card>

      {viagemSelecionada && (
        <RotaModal viagemId={viagemSelecionada} onClose={() => setViagemSelecionada(null)} />
      )}
    </div>
  )
}

export default Pedidos

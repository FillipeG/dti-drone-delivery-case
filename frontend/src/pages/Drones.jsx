import { useEffect, useState } from 'react'
import Card from '../components/ui/Card'
import Badge from '../components/ui/Badge'
import Button from '../components/ui/Button'
import BatteryBar from '../components/ui/BatteryBar'
import { useToast } from '../components/ui/useToast'
import { listarStatusDrones, cadastrarDrone, recarregarDrone } from '../api/drones'
import { droneStatusLabel, droneStatusTone } from '../utils/droneStatus'
import './Drones.css'

const FORM_INICIAL = { capacidadeMaximaPeso: '', autonomiaMaximaKm: '' }

function Drones() {
  const showToast = useToast()
  const [drones, setDrones] = useState([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  const [form, setForm] = useState(FORM_INICIAL)
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState(null)

  const [recarregandoId, setRecarregandoId] = useState(null)

  async function carregarDrones() {
    try {
      setErro(null)
      const dados = await listarStatusDrones()
      setDrones(dados)
    } catch (err) {
      setErro(err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelado = false

    listarStatusDrones()
      .then((dados) => {
        if (!cancelado) {
          setDrones(dados)
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
      await cadastrarDrone({
        capacidadeMaximaPeso: Number(form.capacidadeMaximaPeso),
        autonomiaMaximaKm: Number(form.autonomiaMaximaKm),
      })
      setForm(FORM_INICIAL)
      await carregarDrones()
      showToast('Drone cadastrado!')
    } catch (err) {
      setErroForm(err.message)
    } finally {
      setSalvando(false)
    }
  }

  async function handleRecarregar(id) {
    setRecarregandoId(id)
    try {
      await recarregarDrone(id)
      await carregarDrones()
      showToast('Drone recarregado!')
    } catch (err) {
      setErro(err.message)
    } finally {
      setRecarregandoId(null)
    }
  }

  return (
    <div>
      <h1>Drones</h1>

      <Card title="Cadastrar drone" className="drones-form-card">
        <form className="drones-form" onSubmit={handleSubmit}>
          <label className="drones-form__field">
            <span>Capacidade máxima (kg)</span>
            <input
              type="number"
              min="0.1"
              step="0.1"
              required
              value={form.capacidadeMaximaPeso}
              onChange={(e) => setForm({ ...form, capacidadeMaximaPeso: e.target.value })}
            />
          </label>

          <label className="drones-form__field">
            <span>Autonomia (km)</span>
            <input
              type="number"
              min="0.1"
              step="0.1"
              required
              value={form.autonomiaMaximaKm}
              onChange={(e) => setForm({ ...form, autonomiaMaximaKm: e.target.value })}
            />
          </label>

          <Button type="submit" disabled={salvando}>
            {salvando ? 'Cadastrando...' : 'Cadastrar drone'}
          </Button>
        </form>

        {erroForm && <p className="drones-form__erro">{erroForm}</p>}
      </Card>

      <Card title="Frota" className="drones-list-card">
        {loading && <p>Carregando frota...</p>}
        {erro && <p className="drones-form__erro">{erro}</p>}

        {!loading && !erro && drones.length === 0 && <p>Nenhum drone cadastrado ainda.</p>}

        {!loading && drones.length > 0 && (
          <table className="drones-table">
            <thead>
              <tr>
                <th>Drone</th>
                <th>Status</th>
                <th>Capacidade</th>
                <th>Autonomia</th>
                <th>Bateria</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {drones.map((drone) => (
                <tr key={drone.id}>
                  <td>{drone.id.slice(0, 8)}</td>
                  <td>
                    <Badge tone={droneStatusTone(drone.status)}>{droneStatusLabel(drone.status)}</Badge>
                  </td>
                  <td>{drone.capacidadeMaximaPesoKg} kg</td>
                  <td>{drone.autonomiaMaximaKm} km</td>
                  <td>
                    <BatteryBar percentual={drone.bateriaPercentual} />
                  </td>
                  <td>
                    <Button
                      variant="ghost"
                      disabled={drone.status !== 'IDLE' || recarregandoId === drone.id}
                      onClick={() => handleRecarregar(drone.id)}
                    >
                      {recarregandoId === drone.id ? 'Recarregando...' : 'Recarregar'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}

export default Drones

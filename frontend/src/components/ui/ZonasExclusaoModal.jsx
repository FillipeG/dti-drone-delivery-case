import { useEffect, useState } from 'react'
import Modal from './Modal'
import Button from './Button'
import { listarZonas, cadastrarZona, removerZona } from '../../api/zonasExclusao'
import './ZonasExclusaoModal.css'

const FORM_INICIAL = { nome: '', coordenadaX: '', coordenadaY: '', raioKm: '' }

function ZonasExclusaoModal({ onClose }) {
  const [zonas, setZonas] = useState([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState(null)

  const [form, setForm] = useState(FORM_INICIAL)
  const [salvando, setSalvando] = useState(false)
  const [erroForm, setErroForm] = useState(null)
  const [removendoId, setRemovendoId] = useState(null)

  function carregarZonas() {
    return listarZonas()
      .then((dados) => {
        setZonas(dados)
        setErro(null)
      })
      .catch((err) => setErro(err.message))
  }

  useEffect(() => {
    let cancelado = false

    listarZonas()
      .then((dados) => {
        if (!cancelado) {
          setZonas(dados)
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
      await cadastrarZona({
        nome: form.nome,
        coordenadaX: Number(form.coordenadaX),
        coordenadaY: Number(form.coordenadaY),
        raioKm: Number(form.raioKm),
      })
      setForm(FORM_INICIAL)
      await carregarZonas()
    } catch (err) {
      setErroForm(err.message)
    } finally {
      setSalvando(false)
    }
  }

  async function handleRemover(id) {
    setRemovendoId(id)
    try {
      await removerZona(id)
      await carregarZonas()
    } catch (err) {
      setErro(err.message)
    } finally {
      setRemovendoId(null)
    }
  }

  return (
    <Modal title="Zonas de exclusão" onClose={onClose} width={420}>
      <form className="zonas-modal__form" onSubmit={handleSubmit}>
        <input
          placeholder="Nome"
          required
          value={form.nome}
          onChange={(e) => setForm({ ...form, nome: e.target.value })}
        />
        <div className="zonas-modal__form-linha">
          <input
            type="number"
            step="0.1"
            placeholder="X"
            required
            value={form.coordenadaX}
            onChange={(e) => setForm({ ...form, coordenadaX: e.target.value })}
          />
          <input
            type="number"
            step="0.1"
            placeholder="Y"
            required
            value={form.coordenadaY}
            onChange={(e) => setForm({ ...form, coordenadaY: e.target.value })}
          />
          <input
            type="number"
            min="0.1"
            step="0.1"
            placeholder="Raio (km)"
            required
            value={form.raioKm}
            onChange={(e) => setForm({ ...form, raioKm: e.target.value })}
          />
        </div>
        <Button type="submit" disabled={salvando}>
          {salvando ? 'Cadastrando...' : 'Cadastrar zona'}
        </Button>
      </form>

      {erroForm && <p className="zonas-modal__erro">{erroForm}</p>}

      {loading && <p>Carregando zonas...</p>}
      {erro && <p className="zonas-modal__erro">{erro}</p>}
      {!loading && !erro && zonas.length === 0 && <p>Nenhuma zona cadastrada ainda.</p>}

      {zonas.length > 0 && (
        <ul className="zonas-modal__lista">
          {zonas.map((zona) => (
            <li key={zona.id}>
              <span>
                {zona.nome} — ({zona.coordenadaX}, {zona.coordenadaY}) · raio {zona.raioKm} km
              </span>
              <Button variant="ghost" disabled={removendoId === zona.id} onClick={() => handleRemover(zona.id)}>
                {removendoId === zona.id ? '...' : 'Remover'}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}

export default ZonasExclusaoModal

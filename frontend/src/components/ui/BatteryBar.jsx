import './BatteryBar.css'

function nivelDaBateria(percentual) {
  if (percentual <= 20) return 'baixa'
  if (percentual <= 50) return 'media'
  return 'alta'
}

function BatteryBar({ percentual }) {
  const valor = Math.max(0, Math.min(100, percentual ?? 0))

  return (
    <div className="battery-bar">
      <div className="battery-bar__track">
        <div
          className={`battery-bar__fill battery-bar__fill--${nivelDaBateria(valor)}`}
          style={{ width: `${valor}%` }}
        />
      </div>
      <span className="battery-bar__label">{valor}%</span>
    </div>
  )
}

export default BatteryBar

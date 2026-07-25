import './StatTile.css'

function StatTile({ label, value, tone = 'default' }) {
  return (
    <div className="stat-tile">
      <p className="stat-tile__label">{label}</p>
      <p className={`stat-tile__value stat-tile__value--${tone}`}>{value}</p>
    </div>
  )
}

export default StatTile

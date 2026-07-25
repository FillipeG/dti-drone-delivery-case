import './Badge.css'

// tone controla a cor: neutral, info, success, warning, danger
function Badge({ tone = 'neutral', children }) {
  return <span className={`badge badge--${tone}`}>{children}</span>
}

export default Badge

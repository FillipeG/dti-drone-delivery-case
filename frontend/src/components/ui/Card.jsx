import './Card.css'

function Card({ title, action, children, className = '' }) {
  return (
    <div className={`card${className ? ` ${className}` : ''}`}>
      {(title || action) && (
        <div className="card__header">
          {title && <p className="card__title">{title}</p>}
          {action}
        </div>
      )}
      {children}
    </div>
  )
}

export default Card

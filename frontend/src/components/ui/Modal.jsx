import './Modal.css'

function Modal({ title, onClose, children, width }) {
  return (
    <div className="modal__overlay" onClick={onClose}>
      <div className="modal" style={width ? { width } : undefined} onClick={(e) => e.stopPropagation()}>
        <div className="modal__header">
          <p className="modal__title">{title}</p>
          <button className="modal__fechar" onClick={onClose} aria-label="Fechar">
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

export default Modal

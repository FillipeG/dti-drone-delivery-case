import { useCallback, useState } from 'react'
import { ToastContext } from './ToastContext'
import './Toast.css'

let proximoId = 0

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const showToast = useCallback((mensagem, tipo = 'sucesso') => {
    const id = proximoId++
    setToasts((atual) => [...atual, { id, mensagem, tipo }])
    setTimeout(() => {
      setToasts((atual) => atual.filter((toast) => toast.id !== id))
    }, 3000)
  }, [])

  return (
    <ToastContext.Provider value={showToast}>
      {children}
      <div className="toast-container">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast--${toast.tipo}`}>
            {toast.mensagem}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

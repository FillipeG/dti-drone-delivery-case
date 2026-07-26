import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/layout/Layout'
import { ToastProvider } from './components/ui/Toast'
import Dashboard from './pages/Dashboard'
import Pedidos from './pages/Pedidos'
import Drones from './pages/Drones'

function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <Layout>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/pedidos" element={<Pedidos />} />
            <Route path="/drones" element={<Drones />} />
          </Routes>
        </Layout>
      </ToastProvider>
    </BrowserRouter>
  )
}

export default App

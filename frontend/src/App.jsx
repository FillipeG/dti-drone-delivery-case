import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/layout/Layout'
import Dashboard from './pages/Dashboard'
import Pedidos from './pages/Pedidos'
import Drones from './pages/Drones'

function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/pedidos" element={<Pedidos />} />
          <Route path="/drones" element={<Drones />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  )
}

export default App

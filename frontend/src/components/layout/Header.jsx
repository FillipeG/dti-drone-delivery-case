import { NavLink } from 'react-router-dom'
import './Header.css'

const links = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/pedidos', label: 'Pedidos' },
  { to: '/drones', label: 'Drones' },
]

function Header() {
  return (
    <header className="app-header">
      <div className="app-header__brand">
        <span className="app-header__logo">dti</span>
        <span className="app-header__divider" />
        <span className="app-header__title">simulador de entregas em drone</span>
      </div>

      <nav className="app-header__nav">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) =>
              isActive ? 'app-header__link app-header__link--active' : 'app-header__link'
            }
          >
            {link.label}
          </NavLink>
        ))}
      </nav>

      <div className="app-header__user" title="operador logado">
        OP
      </div>
    </header>
  )
}

export default Header

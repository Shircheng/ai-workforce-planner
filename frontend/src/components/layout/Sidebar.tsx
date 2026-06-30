import { UsersRound } from 'lucide-react'
import { NavLink, useLocation } from 'react-router-dom'

const navItems = [
  { label: 'Dashboard', to: '/' },
  { label: 'Talent Explorer', to: '/talent' },
  { label: 'Opportunity Intake', to: '/opportunities/new' },
  { label: 'Recommendations', to: '/recommendations' },
  { label: 'Analysis', to: '/analysis' },
]

function Sidebar() {
  const location = useLocation()

  return (
    <aside className="sidebar">
      <div className="sidebar-title">
        <span className="sidebar-title-icon" aria-hidden="true">
          <UsersRound size={19} />
        </span>
        <span>AI Workforce Planner</span>
      </div>
      <nav className="sidebar-nav" aria-label="Primary navigation">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              isActive ||
              (item.to === '/recommendations' &&
                location.pathname.endsWith('/recommendations'))
                ? 'sidebar-link active'
                : 'sidebar-link'
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}

export default Sidebar

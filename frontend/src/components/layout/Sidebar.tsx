import { NavLink } from 'react-router-dom'

const navItems = [
  { label: 'Dashboard', to: '/' },
  { label: 'Talent Explorer', to: '/talent' },
  { label: 'Opportunity Intake', to: '/opportunities/new' },
  { label: 'Opportunity List', to: '/opportunities' },
  { label: 'Recommendations', to: '/opportunities/sample/recommendations' },
  { label: 'EWA Review', to: '/ewa' },
  { label: 'Analysis', to: '/analysis' },
]

function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-title">AI Workforce Planner</div>
      <nav className="sidebar-nav" aria-label="Primary navigation">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end
            className={({ isActive }) =>
              isActive ? 'sidebar-link active' : 'sidebar-link'
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

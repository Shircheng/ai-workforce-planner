import { BrowserRouter, Route, Routes } from 'react-router-dom'
import AppLayout from '../components/layout/AppLayout'
import DashboardPage from '../pages/DashboardPage'
import EwaReviewPackPage from '../pages/EwaReviewPackPage'
import OpportunityIntakePage from '../pages/OpportunityIntakePage'
import OpportunityListPage from '../pages/OpportunityListPage'
import PersonProfilePage from '../pages/PersonProfilePage'
import RecommendationPage from '../pages/RecommendationPage'
import TalentExplorerPage from '../pages/TalentExplorerPage'

function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/talent" element={<TalentExplorerPage />} />
          <Route path="/people/:id" element={<PersonProfilePage />} />
          <Route
            path="/people/:id/recommendation"
            element={<PersonProfilePage />}
          />
          <Route
            path="/opportunities/new"
            element={<OpportunityIntakePage />}
          />
          <Route path="/opportunities" element={<OpportunityListPage />} />
          <Route
            path="/opportunities/:id/recommendations"
            element={<RecommendationPage />}
          />
          <Route path="/ewa/:id" element={<EwaReviewPackPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default AppRoutes

import PageHeader from '../components/common/PageHeader'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import AppLayout from '../components/layout/AppLayout'
import AnalysisPage from '../pages/AnalysisPage'
import DashboardPage from '../pages/DashboardPage'
import EwaReviewPackPage from '../pages/EwaReviewPackPage'
import OpportunityIntakePage from '../pages/OpportunityIntakePage'
import PersonProfilePage from '../pages/PersonProfilePage'
import RecommendationPage from '../pages/RecommendationPage'
import TalentExplorerPage from '../pages/TalentExplorerPage'

const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/talent', element: <TalentExplorerPage /> },
      { path: '/people/:id', element: <PersonProfilePage /> },
      { path: '/people/:id/recommendation', element: <PersonProfilePage /> },
      { path: '/opportunities/new', element: <OpportunityIntakePage /> },
      { path: '/team-comparison', element: <PageHeader title="Team Comparison" />},
      { path: '/forecast', element: <PageHeader title="Forecast" />},
      { path: '/recommendations', element: <RecommendationPage /> },
      { path: '/opportunities/:id/recommendations', element: <RecommendationPage /> },
      { path: '/ewa', element: <EwaReviewPackPage /> },
      { path: '/analysis', element: <AnalysisPage />},
    ],
  },
])

function AppRoutes() {
  return <RouterProvider router={router} />
}

export default AppRoutes

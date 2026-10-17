import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext.jsx'
import Landing from './routes/Landing.jsx'
import AppLayout from './routes/AppLayout.jsx'
import ProtectedRoute from './routes/ProtectedRoute.jsx'
import Activities from './routes/Activities.jsx'
import GroupDetail from './routes/GroupDetail.jsx'
import AddExpense from './routes/AddExpense.jsx'
import Notes from './routes/Notes.jsx'
import Create from './routes/Create.jsx'
import Profile from './routes/Profile.jsx'
import NotFound from './routes/NotFound.jsx'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route
          path="/app"
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="activities" replace />} />
          <Route path="activities" element={<Activities />} />
          <Route path="activities/:groupId" element={<GroupDetail />} />
          <Route path="activities/:groupId/add-expense" element={<AddExpense />} />
          <Route path="activities/:groupId/expenses/:expenseId/edit" element={<AddExpense />} />
          <Route path="activities/:groupId/notes" element={<Notes />} />
          <Route path="create" element={<Create />} />
          <Route path="profile" element={<Profile />} />
        </Route>
        <Route path="*" element={<NotFound />} />
      </Routes>
    </AuthProvider>
  )
}

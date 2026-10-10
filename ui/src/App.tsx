import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { UploadPage } from './pages/UploadPage'
import { DocumentsPage } from './pages/DocumentsPage'
import { DocumentDetailPage } from './pages/DocumentDetailPage'
import { TransactionHistoryPage } from './pages/TransactionHistoryPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/documents" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/documents/upload" element={<UploadPage />} />
          <Route path="/documents/:id" element={<DocumentDetailPage />} />
          <Route path="/transactions" element={<TransactionHistoryPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/documents" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App

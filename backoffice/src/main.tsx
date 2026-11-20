import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { httpAuthApi } from './data/auth'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={import.meta.env.BASE_URL}>
      <AuthProvider auth={httpAuthApi()}>
        <App now={new Date()} />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)

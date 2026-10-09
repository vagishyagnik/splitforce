import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import App from './App.jsx'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
        <Toaster
          position="top-center"
          richColors={false}
          closeButton
          toastOptions={{
            style: {
              fontFamily: 'Plus Jakarta Sans, system-ui, sans-serif',
              borderRadius: '14px',
              border: '1px solid rgba(15, 23, 32, 0.10)',
              background: '#FAF8F5',
              color: '#0F1720',
              boxShadow: '0 20px 40px -18px rgba(15,23,32,0.18)',
            },
          }}
        />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)

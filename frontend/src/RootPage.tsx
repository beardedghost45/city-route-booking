import App from './App'
import AdminPage from './AdminPage'

export default function RootPage({ pathname = window.location.pathname }: { pathname?: string }) {
  const normalizedPath = pathname.replace(/\/+$/, '') || '/'
  return normalizedPath === '/admin' ? <AdminPage /> : <App />
}

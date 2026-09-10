import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AlertCircle, RefreshCw, Loader2 } from 'lucide-react'

export function Spinner({ label = 'Loading...' }) {
    return (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
            <Loader2 size={32} className="animate-spin text-red-500" />
            <p className="text-sm text-gray-500">{label}</p>
        </div>
    )
}

export function ErrorBox({ error, onRetry }) {
    if (!error) return null
    const advice = {
        401: 'Your session expired. Please sign in again.',
        403: 'Your account does not have permission for this.',
        409: 'Someone got there first — refresh and try again.',
        502: 'A service this depends on is unreachable.',
        503: 'Temporarily unavailable. Try again in a moment.',
    }[error.status]

    return (
        <div className="flex items-start gap-3 bg-red-950/50 border border-red-800 rounded-xl p-4 mb-4">
            <AlertCircle size={18} className="text-red-400 shrink-0 mt-0.5" />
            <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-red-300">{error.message}</p>
                {advice && <p className="text-sm text-red-500 mt-0.5">{advice}</p>}
            </div>
            {onRetry && (
                <button onClick={onRetry} className="flex items-center gap-1.5 text-xs text-red-400 hover:text-red-300 font-medium shrink-0">
                    <RefreshCw size={13} /> Retry
                </button>
            )}
        </div>
    )
}

export function RequireAuth({ children }) {
    const { isAuthenticated } = useAuth()
    const location = useLocation()
    if (!isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />
    return children
}

export function RequireAdmin({ children }) {
    const { isAuthenticated, isAdmin } = useAuth()
    if (!isAuthenticated) return <Navigate to="/login" replace />
    if (!isAdmin) {
        return (
            <div className="max-w-md mx-auto mt-16 text-center">
                <div className="w-16 h-16 bg-red-950 rounded-full flex items-center justify-center mx-auto mb-4 border border-red-800">
                    <AlertCircle size={28} className="text-red-500" />
                </div>
                <h2 className="text-xl font-semibold text-white mb-2">Access Denied</h2>
                <p className="text-gray-500 text-sm">This page requires the ADMIN role.</p>
            </div>
        )
    }
    return children
}
import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, CheckCircle2, XCircle, Loader2, Activity } from 'lucide-react'
import { get } from '../api/client'

/**
 * We cannot call Eureka directly from the browser (CORS).
 * We cannot call each service directly (not exposed).
 * Solution: ping each service through the API Gateway using
 * existing endpoints we already have. If the endpoint responds
 * with any non-network-error, the service is UP.
 */
const SERVICES = [
    {
        key: 'api-gateway',
        name: 'API Gateway',
        icon: '🌐',
        // Gateway health is public
        check: () => fetch('http://localhost:8083/actuator/health', { signal: AbortSignal.timeout(4000) }),
    },
    {
        key: 'auth-service',
        name: 'Auth Service',
        icon: '🔐',
        // Auth profile needs token but a 401 means the service IS up
        check: () => get('/api/auth/profile').then(() => 'UP').catch((e) => e.status === 401 ? 'UP' : 'DOWN'),
        indirect: true,
    },
    {
        key: 'movie-service',
        name: 'Movie Service',
        icon: '🎬',
        check: () => get('/api/movies').then(() => 'UP').catch(() => 'DOWN'),
        indirect: true,
    },
    {
        key: 'theater-service',
        name: 'Theater Service',
        icon: '🏛️',
        check: () => get('/api/theaters').then(() => 'UP').catch(() => 'DOWN'),
        indirect: true,
    },
    {
        key: 'screen-service',
        name: 'Screen Service',
        icon: '📽️',
        check: () => get('/api/screens').then(() => 'UP').catch(() => 'DOWN'),
        indirect: true,
    },
    {
        key: 'showtime-service',
        name: 'Showtime Service',
        icon: '🕐',
        check: () => get('/api/showtimes').then(() => 'UP').catch(() => 'DOWN'),
        indirect: true,
    },
    {
        key: 'seat-service',
        name: 'Seat Service',
        icon: '💺',
        check: () => get('/api/seats').then(() => 'UP').catch(() => 'DOWN'),
        indirect: true,
    },
    {
        key: 'booking-service',
        name: 'Booking Service',
        icon: '🎟️',
        check: () => get('/api/bookings').then(() => 'UP').catch((e) => e.status === 401 ? 'UP' : 'DOWN'),
        indirect: true,
    },
    {
        key: 'payment-service',
        name: 'Payment Service',
        icon: '💳',
        check: () => fetch('http://localhost:8083/api/payments', {
            signal: AbortSignal.timeout(4000),
            headers: { Authorization: `Bearer ${localStorage.getItem('access_token') ?? ''}` }
        }).then((r) => r.status < 500 ? 'UP' : 'DOWN').catch(() => 'DOWN'),
        indirect: true,
    },
    {
        key: 'user-service',
        name: 'User Service',
        icon: '👤',
        check: () => get('/api/admin/users').then(() => 'UP').catch((e) => e.status === 401 || e.status === 403 ? 'UP' : 'DOWN'),
        indirect: true,
    },
    {
        key: 'admin-service',
        name: 'Admin Service',
        icon: '⚙️',
        check: () => get('/api/admin/users').then(() => 'UP').catch((e) => e.status === 401 || e.status === 403 ? 'UP' : 'DOWN'),
        indirect: true,
    },
]

async function checkService(service) {
    try {
        if (service.indirect) {
            const result = await service.check()
            return typeof result === 'string' ? result : 'UP'
        }
        const res = await service.check()
        if (!res.ok && res.status >= 500) return 'DOWN'
        return 'UP'
    } catch {
        return 'DOWN'
    }
}

export default function HealthTab() {
    const [results, setResults] = useState({})
    const [checking, setChecking] = useState(false)
    const [lastChecked, setLastChecked] = useState(null)

    const runChecks = useCallback(async () => {
        setChecking(true)
        const checks = await Promise.all(
            SERVICES.map(async (s) => {
                const status = await checkService(s)
                return [s.key, status]
            })
        )
        setResults(Object.fromEntries(checks))
        setLastChecked(new Date())
        setChecking(false)
    }, [])

    useEffect(() => { runChecks() }, [runChecks])

    const upCount = Object.values(results).filter((s) => s === 'UP').length
    const downCount = Object.values(results).filter((s) => s === 'DOWN').length
    const total = SERVICES.length
    const allUp = upCount === total

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-base font-semibold text-white flex items-center gap-2">
                        <Activity size={16} className="text-red-400" />
                        Service Health
                    </h2>
                    <p className="text-xs text-gray-500 mt-0.5">
                        {lastChecked ? `Last checked at ${lastChecked.toLocaleTimeString()}` : 'Checking...'}
                        <span className="ml-2 text-gray-600">· via API Gateway</span>
                    </p>
                </div>
                <button
                    onClick={runChecks}
                    disabled={checking}
                    className="flex items-center gap-2 px-4 py-2 bg-gray-800 hover:bg-gray-700 disabled:opacity-50 text-gray-300 text-xs font-medium rounded-lg border border-gray-700 transition-colors"
                >
                    <RefreshCw size={13} className={checking ? 'animate-spin' : ''} />
                    {checking ? 'Checking...' : 'Refresh'}
                </button>
            </div>

            {/* Summary */}
            {Object.keys(results).length > 0 && (
                <div className={`rounded-xl border p-4 mb-6 flex items-center gap-3 ${
                    allUp ? 'bg-green-950/30 border-green-800'
                        : downCount > 0 ? 'bg-red-950/30 border-red-800'
                            : 'bg-gray-800 border-gray-700'
                }`}>
                    {allUp
                        ? <CheckCircle2 size={20} className="text-green-500 shrink-0" />
                        : <XCircle size={20} className="text-red-500 shrink-0" />
                    }
                    <div>
                        <p className={`text-sm font-semibold ${allUp ? 'text-green-400' : 'text-red-400'}`}>
                            {allUp ? 'All systems operational' : `${downCount} service(s) unreachable`}
                        </p>
                        <p className="text-xs text-gray-500 mt-0.5">
                            {upCount} up · {downCount} down out of {total} services
                        </p>
                    </div>
                </div>
            )}

            {/* Grid */}
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {SERVICES.map((service) => {
                    const status = results[service.key]
                    return (
                        <div
                            key={service.key}
                            className={`bg-gray-900 rounded-xl border p-4 flex items-center gap-3 transition-colors ${
                                status === 'UP' ? 'border-green-900/50'
                                    : status === 'DOWN' ? 'border-red-900/30'
                                        : 'border-gray-800'
                            }`}
                        >
                            <div className="text-xl shrink-0 w-8 text-center">{service.icon}</div>
                            <div className="flex-1 min-w-0">
                                <p className="text-sm font-semibold text-white leading-tight">{service.name}</p>
                                <p className="text-xs text-gray-600 mt-0.5">
                                    {service.indirect ? 'via gateway' : 'direct'}
                                </p>
                            </div>
                            <div className="shrink-0">
                                {!status || checking && !status ? (
                                    <div className="flex items-center gap-1.5 bg-gray-800 text-gray-400 text-xs font-medium px-2.5 py-1 rounded-full border border-gray-700">
                                        <Loader2 size={11} className="animate-spin" /> Checking
                                    </div>
                                ) : status === 'UP' ? (
                                    <div className="flex items-center gap-1.5 bg-green-950/50 text-green-400 text-xs font-bold px-2.5 py-1 rounded-full border border-green-800">
                                        <div className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" /> UP
                                    </div>
                                ) : (
                                    <div className="flex items-center gap-1.5 bg-red-950/50 text-red-400 text-xs font-bold px-2.5 py-1 rounded-full border border-red-800">
                                        <div className="w-1.5 h-1.5 rounded-full bg-red-500" /> DOWN
                                    </div>
                                )}
                            </div>
                        </div>
                    )
                })}
            </div>

            <div className="mt-6 bg-gray-800/50 border border-gray-700 rounded-xl p-4">
                <p className="text-xs text-gray-500 leading-relaxed">
                    <span className="text-gray-400 font-medium">How this works:</span> Each service is pinged through
                    the API Gateway at <code className="text-gray-300 bg-gray-700 px-1 rounded">localhost:8083</code>.
                    A <span className="text-green-400">UP</span> status means the gateway successfully routed the request
                    to that service. A 401/403 response still counts as UP — it means the service is running but requires auth.
                    For a definitive check run <code className="text-gray-300 bg-gray-700 px-1 rounded">bash scripts/health-check.sh</code>.
                </p>
            </div>
        </div>
    )
}
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { bookings as bookingsApi } from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { ErrorBox, Spinner } from '../components/Common'
import BakongModal from '../components/BakongModal'
import { Ticket, X, CheckCircle2, Clock, XCircle } from 'lucide-react'

const STATUS = {
    CONFIRMED: {
        label: 'Confirmed',
        cls: 'bg-green-950/50 text-green-400 border border-green-800',
        icon: <CheckCircle2 size={13} />,
    },
    PENDING: {
        label: 'Pending Payment',
        cls: 'bg-yellow-950/50 text-yellow-400 border border-yellow-800',
        icon: <Clock size={13} />,
    },
    CANCELLED: {
        label: 'Cancelled',
        cls: 'bg-gray-800 text-gray-500 border border-gray-700',
        icon: <XCircle size={13} />,
    },
}

export default function Bookings() {
    const { user } = useAuth()
    const [params] = useSearchParams()
    const justCreated = params.get('created')

    const [list, setList] = useState(null)
    const [error, setError] = useState(null)
    const [busyId, setBusyId] = useState(null)
    const [payingBooking, setPayingBooking] = useState(null) // booking being paid via Bakong

    const load = useCallback(() => {
        bookingsApi
            .byUser(user.userId)
            .then((data) => { setList(data); setError(null) })
            .catch(setError)
    }, [user.userId])

    useEffect(() => { load() }, [load])

    async function cancel(booking) {
        if (!confirm('Cancel this booking? The seat will be released.')) return
        setBusyId(booking.bookingId)
        setError(null)
        try {
            await bookingsApi.cancel(booking.bookingId)
            load()
        } catch (err) {
            setError(err)
        } finally {
            setBusyId(null)
        }
    }

    if (error && !list) return <ErrorBox error={error} onRetry={load} />
    if (!list) return <Spinner />

    return (
        <div>
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-white">My Bookings</h1>
                <p className="text-gray-500 text-sm mt-1">Your booking history</p>
            </div>

            {justCreated && (
                <div className="flex items-center gap-3 bg-green-950/50 border border-green-800 rounded-xl p-4 mb-6">
                    <CheckCircle2 size={20} className="text-green-500 shrink-0" />
                    <div>
                        <p className="text-sm font-semibold text-green-400">Booking #{justCreated} created!</p>
                        <p className="text-xs text-green-600 mt-0.5">Complete payment below to confirm your seat.</p>
                    </div>
                </div>
            )}

            <ErrorBox error={error} />

            {list.length === 0 ? (
                <div className="text-center py-24">
                    <div className="w-16 h-16 bg-gray-800 rounded-full flex items-center justify-center mx-auto mb-4 border border-gray-700">
                        <Ticket size={28} className="text-gray-600" />
                    </div>
                    <h2 className="text-lg font-semibold text-gray-400 mb-2">No bookings yet</h2>
                    <p className="text-gray-600 text-sm">Book a movie seat to see it here.</p>
                </div>
            ) : (
                <div className="space-y-3">
                    {list.map((b) => {
                        const status = STATUS[b.bookingStatus] ?? {
                            label: b.bookingStatus,
                            cls: 'bg-gray-800 text-gray-400 border border-gray-700',
                            icon: null,
                        }
                        const isBusy = busyId === b.bookingId

                        return (
                            <div
                                key={b.bookingId}
                                className="bg-gray-900 rounded-xl border border-gray-800 p-5 flex flex-col sm:flex-row sm:items-center gap-4 hover:border-gray-700 transition-colors"
                            >
                                {/* Icon */}
                                <div className="w-12 h-12 bg-red-950/50 rounded-xl flex items-center justify-center shrink-0 border border-red-900/50">
                                    <Ticket size={22} className="text-red-500" />
                                </div>

                                {/* Details */}
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                                        <span className="text-sm font-bold text-white">Booking #{b.bookingId}</span>
                                        <span className={`inline-flex items-center gap-1 text-xs font-medium px-2.5 py-0.5 rounded-full ${status.cls}`}>
                      {status.icon} {status.label}
                    </span>
                                    </div>
                                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-500">
                                        <span>Seat #{b.seatId}</span>
                                        <span>Showtime #{b.showtimeId}</span>
                                        <span className="font-semibold text-gray-300">${b.totalAmount}</span>
                                    </div>
                                </div>

                                {/* Actions */}
                                <div className="flex items-center gap-2 shrink-0">
                                    {b.bookingStatus === 'PENDING' && (
                                        <button
                                            disabled={isBusy}
                                            onClick={() => setPayingBooking(b)}
                                            className="flex items-center gap-2 bg-[#E30613] hover:bg-red-700 disabled:opacity-50 text-white text-xs font-bold px-4 py-2 rounded-lg transition-colors shadow-lg shadow-red-900/30"
                                        >
                                            {/* Bakong B logo */}
                                            <div className="w-4 h-4 bg-white rounded flex items-center justify-center">
                                                <span className="text-[#E30613] font-black text-xs leading-none">B</span>
                                            </div>
                                            Pay with Bakong
                                        </button>
                                    )}
                                    {b.bookingStatus !== 'CANCELLED' && (
                                        <button
                                            disabled={isBusy}
                                            onClick={() => cancel(b)}
                                            className="flex items-center gap-1.5 text-gray-500 hover:text-red-400 disabled:opacity-50 text-xs font-medium px-3 py-2 rounded-lg hover:bg-red-950/30 transition-colors"
                                        >
                                            <X size={13} /> Cancel
                                        </button>
                                    )}
                                </div>
                            </div>
                        )
                    })}
                </div>
            )}

            {/* Bakong Payment Modal */}
            {payingBooking && (
                <BakongModal
                    booking={payingBooking}
                    onClose={() => setPayingBooking(null)}
                    onSuccess={() => { setPayingBooking(null); load() }}
                />
            )}
        </div>
    )
}
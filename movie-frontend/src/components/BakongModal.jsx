import { useCallback, useEffect, useRef, useState } from 'react'
import { X, CheckCircle2, Clock, Smartphone, AlertTriangle, Loader2 } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import { payments as paymentsApi } from '../api/endpoints'
import toast from 'react-hot-toast'

// How often to ask payment-service whether Bakong has seen the money.
const POLL_MS = 3000

/**
 * Real Bakong KHQR checkout.
 *
 * payment-service issues the QR (the amount is the booking's own, never
 * sent from here) and is the only thing that can mark it paid — it does so
 * when the Bakong Open API reports the transaction. This screen just shows
 * the QR and polls until the status is final. Closing it does not lose a
 * payment: a background job on the server keeps checking.
 */
export default function BakongModal({ booking, onClose, onSuccess }) {
    // loading | qr | paid | expired | error
    const [step, setStep] = useState('loading')
    const [payment, setPayment] = useState(null)
    const [error, setError] = useState(null)
    const [checking, setChecking] = useState(false)
    const [remaining, setRemaining] = useState(0)
    const doneTimer = useRef(null)

    // The parent passes a fresh arrow each render. Reading it through a ref
    // keeps applyPayment stable, so the create effect below runs once
    // instead of issuing a request every time the bookings list re-renders.
    const onSuccessRef = useRef(onSuccess)
    useEffect(() => { onSuccessRef.current = onSuccess }, [onSuccess])

    const usd = Number(payment?.amount ?? booking.totalAmount).toFixed(2)
    const khr = Math.round(Number(payment?.amount ?? booking.totalAmount) * 4100).toLocaleString()

    const applyPayment = useCallback((p) => {
        setPayment(p)
        if (p.paymentStatus === 'PAID') {
            setStep('paid')
            toast.success('Payment received!')
            doneTimer.current = setTimeout(() => { onSuccessRef.current() }, 2500)
        } else if (p.paymentStatus === 'PENDING') {
            setStep('qr')
            setRemaining(p.secondsRemaining)
        } else {
            // EXPIRED, CANCELLED, FAILED — the booking has been released.
            setStep('expired')
        }
    }, [])

    // Issue (or re-fetch the still-valid) QR for this booking.
    useEffect(() => {
        let cancelled = false
        paymentsApi.createBakong(booking.bookingId)
            .then((p) => { if (!cancelled) applyPayment(p) })
            .catch((err) => {
                if (cancelled) return
                setError(err)
                // 409: already paid, or the QR expired and the booking was
                // cancelled. Either way the list needs reloading, not a retry.
                setStep(err.status === 409 ? 'expired' : 'error')
            })
        return () => { cancelled = true }
    }, [booking.bookingId, applyPayment])

    // Poll while the QR is live.
    useEffect(() => {
        if (step !== 'qr' || !payment) return
        const id = setInterval(async () => {
            try {
                setChecking(true)
                applyPayment(await paymentsApi.checkBakong(payment.paymentId))
            } catch (err) {
                // 502/503: Bakong or payment-service is briefly unreachable.
                // We cannot tell "unpaid" from "cannot check", so keep polling.
                if (err.status !== 502 && err.status !== 503) setError(err)
            } finally {
                setChecking(false)
            }
        }, POLL_MS)
        return () => clearInterval(id)
    }, [step, payment, applyPayment])

    // Local countdown between polls.
    useEffect(() => {
        if (step !== 'qr') return
        const id = setInterval(() => setRemaining((s) => Math.max(0, s - 1)), 1000)
        return () => clearInterval(id)
    }, [step])

    useEffect(() => () => clearTimeout(doneTimer.current), [])

    // Close on Escape
    useEffect(() => {
        function onKey(e) { if (e.key === 'Escape') onClose() }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [onClose])

    // Lock scroll
    useEffect(() => {
        document.body.style.overflow = 'hidden'
        return () => { document.body.style.overflow = '' }
    }, [])

    function handleBackdrop(e) {
        if (e.target === e.currentTarget) onClose()
    }

    const mm = String(Math.floor(remaining / 60)).padStart(2, '0')
    const ss = String(remaining % 60).padStart(2, '0')

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm"
            onClick={handleBackdrop}
        >
            <div className="relative w-full max-w-sm bg-gray-900 rounded-2xl overflow-hidden shadow-2xl border border-gray-800">

                {/* Header */}
                <div className="bg-[#E30613] px-5 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <div className="w-8 h-8 bg-white rounded-lg flex items-center justify-center">
                            <span className="text-[#E30613] font-black text-sm">B</span>
                        </div>
                        <div>
                            <p className="text-white font-bold text-sm leading-none">KHQR</p>
                            <p className="text-red-200 text-xs">Pay with any Bakong member bank</p>
                        </div>
                    </div>
                    <button onClick={onClose} className="text-white/70 hover:text-white transition-colors">
                        <X size={18} />
                    </button>
                </div>

                {step === 'loading' && (
                    <div className="p-10 flex flex-col items-center gap-3">
                        <Loader2 size={32} className="text-[#E30613] animate-spin" />
                        <p className="text-gray-400 text-sm">Generating your KHQR…</p>
                    </div>
                )}

                {step === 'qr' && payment && (
                    <div className="p-5">
                        <div className="text-center mb-4">
                            <p className="text-gray-400 text-xs mb-1">Amount Due</p>
                            <p className="text-3xl font-black text-white">${usd}</p>
                            <p className="text-gray-500 text-xs mt-0.5">≈ {khr} KHR</p>
                        </div>

                        <div className="flex justify-center mb-3">
                            <div className="p-3 bg-white rounded-xl shadow-lg">
                                <QRCodeSVG value={payment.qrString} size={220} level="M" />
                            </div>
                        </div>

                        <div className="flex items-center justify-center gap-4 text-xs mb-4">
                            <span className={`flex items-center gap-1 ${remaining < 60 ? 'text-red-400' : 'text-gray-400'}`}>
                                <Clock size={12} /> Expires in {mm}:{ss}
                            </span>
                            <span className="flex items-center gap-1 text-gray-500">
                                <Loader2 size={12} className={checking ? 'animate-spin' : ''} /> Waiting for payment
                            </span>
                        </div>

                        <div className="bg-gray-800 rounded-xl p-3 mb-3 space-y-2">
                            <p className="text-xs font-semibold text-gray-300 mb-2">How to pay:</p>
                            {[
                                'Open ABA, ACLEDA, Wing, Bakong or any KHQR bank app',
                                'Tap "Scan QR" and scan the code above',
                                'Check the amount and confirm in the app',
                                'This screen updates by itself within a few seconds',
                            ].map((text, i) => (
                                <div key={i} className="flex items-center gap-2.5">
                                    <div className="w-5 h-5 bg-[#E30613] rounded-full flex items-center justify-center text-white text-xs font-bold shrink-0">
                                        {i + 1}
                                    </div>
                                    <p className="text-gray-400 text-xs">{text}</p>
                                </div>
                            ))}
                        </div>

                        <div className="flex items-center justify-between text-xs text-gray-500 px-1">
                            <span>Booking #{booking.bookingId}</span>
                            <span className="flex items-center gap-1"><Smartphone size={11} /> Payment #{payment.paymentId}</span>
                        </div>

                        {error && (
                            <div className="bg-red-950/50 border border-red-800 rounded-lg p-3 mt-3">
                                <p className="text-red-400 text-xs">{error.message}</p>
                            </div>
                        )}
                    </div>
                )}

                {step === 'paid' && (
                    <div className="p-8 flex flex-col items-center justify-center gap-4 text-center">
                        <div className="w-16 h-16 bg-green-950/50 border border-green-700 rounded-full flex items-center justify-center">
                            <CheckCircle2 size={32} className="text-green-500" />
                        </div>
                        <div>
                            <p className="text-white font-bold text-lg">Payment Received</p>
                            <p className="text-gray-400 text-sm mt-1">Your booking is confirmed</p>
                            <p className="text-2xl font-black text-green-400 mt-2">${usd}</p>
                            {payment?.transactionId && (
                                <p className="text-gray-600 text-[10px] mt-2 break-all">Ref {payment.transactionId}</p>
                            )}
                        </div>
                    </div>
                )}

                {step === 'expired' && (
                    <div className="p-8 flex flex-col items-center justify-center gap-4 text-center">
                        <AlertTriangle size={32} className="text-yellow-500" />
                        <div>
                            <p className="text-white font-bold">This payment can no longer be made</p>
                            <p className="text-gray-400 text-sm mt-1">
                                {error?.message ?? 'The QR expired before it was paid, so the seat was released. Please book again.'}
                            </p>
                        </div>
                        <button
                            onClick={onSuccess}
                            className="w-full bg-gray-800 hover:bg-gray-700 text-white font-semibold py-2.5 rounded-xl text-sm"
                        >
                            Back to my bookings
                        </button>
                    </div>
                )}

                {step === 'error' && (
                    <div className="p-8 flex flex-col items-center justify-center gap-4 text-center">
                        <AlertTriangle size={32} className="text-red-500" />
                        <div>
                            <p className="text-white font-bold">Could not start the payment</p>
                            <p className="text-gray-400 text-sm mt-1">{error?.message}</p>
                        </div>
                        <button
                            onClick={onClose}
                            className="w-full bg-gray-800 hover:bg-gray-700 text-white font-semibold py-2.5 rounded-xl text-sm"
                        >
                            Close
                        </button>
                    </div>
                )}
            </div>
        </div>
    )
}

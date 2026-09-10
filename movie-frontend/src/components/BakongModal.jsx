import { useEffect, useRef, useState } from 'react'
import { X, CheckCircle2, Shield, Smartphone } from 'lucide-react'
import { payments as paymentsApi } from '../api/endpoints'
import toast from 'react-hot-toast'

// Fake QR SVG — looks like a real QR code pattern
function FakeQR({ amount }) {
    // Generate a deterministic-looking QR grid based on amount
    const size = 21
    const cells = []
    const seed = amount * 7919 // prime multiply for pseudo-random look

    // Fixed corner squares (real QR always has these)
    const cornerPositions = new Set()
    // Top-left corner
    for (let r = 0; r < 7; r++) for (let c = 0; c < 7; c++) cornerPositions.add(`${r},${c}`)
    // Top-right corner
    for (let r = 0; r < 7; r++) for (let c = 14; c < 21; c++) cornerPositions.add(`${r},${c}`)
    // Bottom-left corner
    for (let r = 14; r < 21; r++) for (let c = 0; c < 7; c++) cornerPositions.add(`${r},${c}`)

    for (let r = 0; r < size; r++) {
        for (let c = 0; c < size; c++) {
            const key = `${r},${c}`
            let filled = false
            if (cornerPositions.has(key)) {
                // Draw finder pattern borders and centers
                const inTL = r < 7 && c < 7
                const inTR = r < 7 && c >= 14
                const inBL = r >= 14 && c < 7

                if (inTL || inTR || inBL) {
                    const lr = inBL ? r - 14 : r
                    const lc = inTR ? c - 14 : c
                    // Outer border
                    if (lr === 0 || lr === 6 || lc === 0 || lc === 6) filled = true
                    // Inner square
                    else if (lr >= 2 && lr <= 4 && lc >= 2 && lc <= 4) filled = true
                    else filled = false
                }
            } else {
                // Data area — pseudo-random based on seed
                const hash = ((r * 31 + c) * seed + r * c * 17) % 100
                filled = hash > 45
            }
            cells.push({ r, c, filled })
        }
    }

    const cell = 10
    const total = size * cell

    return (
        <svg width={total} height={total} viewBox={`0 0 ${total} ${total}`} xmlns="http://www.w3.org/2000/svg">
            <rect width={total} height={total} fill="white" />
            {cells.map(({ r, c, filled }) =>
                filled ? (
                    <rect key={`${r},${c}`} x={c * cell} y={r * cell} width={cell} height={cell} fill="#1a1a2e" />
                ) : null
            )}
            {/* Bakong logo in center */}
            <rect x={total/2 - 18} y={total/2 - 18} width={36} height={36} fill="white" />
            <rect x={total/2 - 14} y={total/2 - 14} width={28} height={28} rx={4} fill="#E30613" />
            <text x={total/2} y={total/2 + 6} textAnchor="middle" fill="white" fontSize="14" fontWeight="bold">B</text>
        </svg>
    )
}

export default function BakongModal({ booking, onClose, onSuccess }) {
    const [step, setStep] = useState('qr') // 'qr' | 'processing' | 'done'
    const [error, setError] = useState(null)
    const timerRef = useRef(null)

    const amount = booking.totalAmount
    const usd = Number(amount).toFixed(2)
    const khr = Math.round(Number(amount) * 4100).toLocaleString()

    // Close on backdrop click
    function handleBackdrop(e) {
        if (e.target === e.currentTarget && step !== 'processing') onClose()
    }

    // Close on Escape
    useEffect(() => {
        function onKey(e) { if (e.key === 'Escape' && step !== 'processing') onClose() }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [onClose, step])

    // Lock scroll
    useEffect(() => {
        document.body.style.overflow = 'hidden'
        return () => { document.body.style.overflow = '' }
    }, [])

    async function confirmPayment() {
        setStep('processing')
        setError(null)
        try {
            const payment = await paymentsApi.create({
                bookingId: booking.bookingId,
                amount: booking.totalAmount,
                paymentMethod: 'BAKONG',
            })
            await paymentsApi.pay(payment.paymentId, `BAKONG-${payment.paymentId}-${Date.now()}`)
            setStep('done')
            toast.success('Payment successful!')
            // Auto close after 2.5s and call onSuccess
            timerRef.current = setTimeout(() => { onSuccess(); onClose() }, 2500)
        } catch (err) {
            setError(err)
            setStep('qr')
        }
    }

    useEffect(() => () => clearTimeout(timerRef.current), [])

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
                            <p className="text-white font-bold text-sm leading-none">Bakong</p>
                            <p className="text-red-200 text-xs">NBC Cambodia</p>
                        </div>
                    </div>
                    {step !== 'processing' && (
                        <button onClick={onClose} className="text-white/70 hover:text-white transition-colors">
                            <X size={18} />
                        </button>
                    )}
                </div>

                {/* QR Step */}
                {step === 'qr' && (
                    <div className="p-5">
                        {/* Amount */}
                        <div className="text-center mb-4">
                            <p className="text-gray-400 text-xs mb-1">Amount Due</p>
                            <p className="text-3xl font-black text-white">${usd}</p>
                            <p className="text-gray-500 text-xs mt-0.5">≈ {khr} KHR</p>
                        </div>

                        {/* QR Code */}
                        <div className="flex justify-center mb-4">
                            <div className="p-3 bg-white rounded-xl shadow-lg">
                                <FakeQR amount={Number(amount)} />
                            </div>
                        </div>

                        {/* Instructions */}
                        <div className="bg-gray-800 rounded-xl p-3 mb-4 space-y-2">
                            <p className="text-xs font-semibold text-gray-300 mb-2">How to pay:</p>
                            {[
                                { step: '1', text: 'Open your Bakong app' },
                                { step: '2', text: 'Tap "Scan QR" button' },
                                { step: '3', text: 'Scan the QR code above' },
                                { step: '4', text: 'Confirm payment in the app' },
                            ].map((s) => (
                                <div key={s.step} className="flex items-center gap-2.5">
                                    <div className="w-5 h-5 bg-[#E30613] rounded-full flex items-center justify-center text-white text-xs font-bold shrink-0">
                                        {s.step}
                                    </div>
                                    <p className="text-gray-400 text-xs">{s.text}</p>
                                </div>
                            ))}
                        </div>

                        {/* Merchant info */}
                        <div className="flex items-center justify-between text-xs text-gray-500 mb-4 px-1">
                            <span className="flex items-center gap-1"><Shield size={11} /> Secure payment</span>
                            <span>Booking #{booking.bookingId}</span>
                            <span className="flex items-center gap-1"><Smartphone size={11} /> NBC Licensed</span>
                        </div>

                        {error && (
                            <div className="bg-red-950/50 border border-red-800 rounded-lg p-3 mb-3">
                                <p className="text-red-400 text-xs">{error.message}</p>
                            </div>
                        )}

                        {/* Simulate payment button (since this is fake) */}
                        <button
                            onClick={confirmPayment}
                            className="w-full bg-[#E30613] hover:bg-red-700 text-white font-bold py-3 rounded-xl text-sm transition-colors shadow-lg"
                        >
                            Simulate Payment ✓
                        </button>
                        <p className="text-center text-xs text-gray-600 mt-2">
                            (Demo only — simulates a successful Bakong payment)
                        </p>
                    </div>
                )}

                {/* Processing Step */}
                {step === 'processing' && (
                    <div className="p-8 flex flex-col items-center justify-center gap-4">
                        <div className="w-16 h-16 border-4 border-[#E30613] border-t-transparent rounded-full animate-spin" />
                        <p className="text-white font-semibold">Processing payment...</p>
                        <p className="text-gray-500 text-sm">Please wait</p>
                    </div>
                )}

                {/* Success Step */}
                {step === 'done' && (
                    <div className="p-8 flex flex-col items-center justify-center gap-4 text-center">
                        <div className="w-16 h-16 bg-green-950/50 border border-green-700 rounded-full flex items-center justify-center">
                            <CheckCircle2 size={32} className="text-green-500" />
                        </div>
                        <div>
                            <p className="text-white font-bold text-lg">Payment Successful!</p>
                            <p className="text-gray-400 text-sm mt-1">Your booking is now confirmed</p>
                            <p className="text-2xl font-black text-green-400 mt-2">${usd}</p>
                        </div>
                        <p className="text-gray-600 text-xs">Redirecting...</p>
                    </div>
                )}
            </div>
        </div>
    )
}
import { X } from 'lucide-react'

// Seat numbers are "<row letters><column>" (A1, B12), which is how both the
// admin bulk-add and seed-demo-data.sh create them. Anything else is shown
// in a trailing row rather than dropped.
const OTHER_ROW = '·'

function parseSeatNumber(seatNumber) {
    const m = /^([A-Za-z]+)\s*-?\s*(\d+)$/.exec(String(seatNumber ?? '').trim())
    return m ? { row: m[1].toUpperCase(), col: Number(m[2]) } : { row: OTHER_ROW, col: 0 }
}

const seatId = (seat) => seat.seatId ?? seat.id

const isStandard = (seat) => !seat.seatType || seat.seatType.toUpperCase() === 'STANDARD'

const prettyType = (type) =>
    type ? type.charAt(0).toUpperCase() + type.slice(1).toLowerCase() : 'Standard'

/** Groups seats into rows (A at the front) with seats placed by column. */
function buildRows(seats) {
    const rows = new Map()
    for (const seat of seats) {
        const { row, col } = parseSeatNumber(seat.seatNumber)
        if (!rows.has(row)) rows.set(row, [])
        rows.get(row).push({ seat, col })
    }

    const maxCol = Math.max(0, ...seats.map((s) => parseSeatNumber(s.seatNumber).col))

    return {
        maxCol,
        rows: [...rows.entries()]
            .sort(([a], [b]) => (a === OTHER_ROW) - (b === OTHER_ROW) || a.localeCompare(b))
            .map(([row, list]) => ({
                row,
                seats: list.sort((a, b) => a.col - b.col),
            })),
    }
}

/** Cheapest price per seat type, for the legend. */
function priceByType(seats) {
    const prices = new Map()
    for (const s of seats) {
        const type = prettyType(s.seatType)
        const price = Number(s.price ?? 0)
        if (!prices.has(type) || price < prices.get(type)) prices.set(type, price)
    }
    return [...prices.entries()].sort((a, b) => a[1] - b[1])
}

function Seat({ seat, label, selected, onToggle }) {
    const free = seat.status === 'AVAILABLE'
    const premium = !isStandard(seat)
    const price = Number(seat.price ?? 0).toFixed(2)

    const why = seat.status === 'BLOCKED' ? 'unavailable' : 'already booked'
    const title = free
        ? `${seat.seatNumber} · ${prettyType(seat.seatType)} · $${price}`
        : `${seat.seatNumber} · ${why}`

    const look = selected
        ? 'bg-red-600 border-red-400 text-white shadow-lg shadow-red-900/50 -translate-y-0.5'
        : free
            ? premium
                ? 'bg-amber-950/40 border-amber-500/70 text-amber-200 hover:bg-amber-500 hover:text-gray-950'
                : 'bg-gray-800 border-gray-600 text-gray-300 hover:bg-red-500 hover:border-red-400 hover:text-white'
            : 'bg-gray-900 border-gray-800 text-gray-700 cursor-not-allowed'

    return (
        <button
            type="button"
            disabled={!free}
            onClick={() => onToggle(seat)}
            title={title}
            aria-label={title}
            aria-pressed={selected}
            className={`relative w-8 h-8 sm:w-9 sm:h-9 shrink-0 rounded-t-lg rounded-b-sm border-2 border-b-4
                text-[11px] font-bold transition-all duration-150 ${look}`}
        >
            {free || selected ? label : <X size={14} className="mx-auto" />}
        </button>
    )
}

function LegendSwatch({ className, children }) {
    return (
        <span className="flex items-center gap-1.5">
            <span className={`w-4 h-4 rounded-t-md rounded-b-sm border-2 border-b-[3px] ${className}`} />
            {children}
        </span>
    )
}

/**
 * The seat grid for one showtime. Statuses come from
 * GET /api/seats/screen/{id}?showtimeId=…, so BOOKED means "booked for this
 * show"; the same seat may be free at another time.
 */
export default function SeatMap({ seats, selectedSeats, onToggle, onClear }) {
    if (seats.length === 0) {
        return <p className="text-center text-gray-500 text-sm py-6">No seats for this screen yet.</p>
    }

    const { rows, maxCol } = buildRows(seats)
    // An aisle down the middle once rows are wide enough to need one.
    const aisleAfter = maxCol >= 8 ? Math.ceil(maxCol / 2) : null
    const isSelected = (seat) => selectedSeats.some((s) => seatId(s) === seatId(seat))
    const available = seats.filter((s) => s.status === 'AVAILABLE').length

    return (
        <div>
            {/* Legend */}
            <div className="flex items-center justify-center gap-x-5 gap-y-2 mb-6 text-xs text-gray-400 flex-wrap">
                <LegendSwatch className="bg-gray-800 border-gray-600">Available ({available})</LegendSwatch>
                <LegendSwatch className="bg-red-600 border-red-400">Selected ({selectedSeats.length})</LegendSwatch>
                <LegendSwatch className="bg-gray-900 border-gray-800">Taken</LegendSwatch>
            </div>

            {/* Screen */}
            <div className="mb-8 px-6">
                <div className="h-6 rounded-[50%] border-t-4 border-red-500/70 shadow-[0_-10px_25px_-10px_rgba(239,68,68,0.6)]" />
                <p className="-mt-3 text-center text-[10px] text-gray-500 tracking-[0.4em] uppercase">Screen</p>
            </div>

            {/* Rows */}
            <div className="overflow-x-auto pb-2">
                <div className="w-max mx-auto space-y-2">
                    {rows.map(({ row, seats: rowSeats }) => {
                        const byCol = new Map(rowSeats.map((s) => [s.col, s.seat]))
                        // Seats with a numeric column sit at their position, so a
                        // missing seat leaves a gap instead of shifting the row.
                        const cells = row === OTHER_ROW
                            ? rowSeats.map(({ seat }, i) => ({ key: `o${i}`, seat, label: seat.seatNumber }))
                            : Array.from({ length: maxCol }, (_, i) => ({
                                key: i + 1,
                                seat: byCol.get(i + 1),
                                label: i + 1,
                            }))

                        return (
                            <div key={row} className="flex items-center gap-1.5">
                                <span className="w-5 text-right text-xs font-semibold text-gray-500">{row}</span>
                                {cells.map(({ key, seat, label }) => (
                                    <div key={key} className={`flex ${aisleAfter && key === aisleAfter ? 'mr-5' : ''}`}>
                                        {seat ? (
                                            <Seat seat={seat} label={label} selected={isSelected(seat)} onToggle={onToggle} />
                                        ) : (
                                            <span className="w-8 h-8 sm:w-9 sm:h-9 shrink-0" />
                                        )}
                                    </div>
                                ))}
                                <span className="w-5 text-xs font-semibold text-gray-500">{row}</span>
                            </div>
                        )
                    })}
                </div>
            </div>

            {/* Prices */}
            <div className="mt-5 flex items-center justify-center gap-2 flex-wrap">
                {priceByType(seats).map(([type, price]) => (
                    <span
                        key={type}
                        className={`text-xs px-2.5 py-1 rounded-full border ${type === 'Standard'
                            ? 'border-gray-700 text-gray-400'
                            : 'border-amber-700/60 text-amber-300'}`}
                    >
                        {type} · ${price.toFixed(2)}
                    </span>
                ))}
            </div>

            {/* Selected */}
            {selectedSeats.length > 0 && (
                <div className="mt-5 flex flex-wrap items-center gap-2">
                    {selectedSeats.map((s) => (
                        <span
                            key={seatId(s)}
                            className="flex items-center gap-1.5 bg-red-950/50 border border-red-800 text-red-200 text-xs font-medium px-2.5 py-1 rounded-full"
                        >
                            {s.seatNumber}
                            <button type="button" onClick={() => onToggle(s)} className="hover:text-white" aria-label={`Remove ${s.seatNumber}`}>
                                <X size={11} />
                            </button>
                        </span>
                    ))}
                    <button
                        type="button"
                        onClick={onClear}
                        className="text-xs text-gray-500 hover:text-gray-300 px-2.5 py-1 rounded-full border border-gray-700 hover:border-gray-600"
                    >
                        Clear all
                    </button>
                </div>
            )}
        </div>
    )
}

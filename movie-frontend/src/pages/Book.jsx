import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    screens as screensApi,
    seats as seatsApi,
    bookings as bookingsApi,
    showtimes as showtimesApi,
    movies as moviesApi,
    theaters as theatersApi,
} from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { ErrorBox, Spinner } from '../components/Common'
import { searchMovie, posterUrl } from '../api/useTMDB'
import { CheckCircle2, Film, Building2, Monitor, Clock, ChevronRight, X } from 'lucide-react'
import toast from 'react-hot-toast'

// Display only. booking-service prices each booking from the seat row and
// Bakong charges exactly that, so the summary must use the same number.
const seatPrice = (seat) => Number(seat.price ?? 0)

// Handle both seatId and id field names from backend
const getSeatId = (seat) => seat.seatId ?? seat.id

function Steps({ current }) {
    const steps = ['Movie', 'Theater', 'Screen & Time', 'Seats', 'Confirm']
    return (
        <div className="flex items-center gap-1 mb-8 overflow-x-auto pb-1">
            {steps.map((label, i) => {
                const idx = i + 1
                const done = current > idx
                const active = current === idx
                return (
                    <div key={label} className="flex items-center gap-1 shrink-0">
                        <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all
              ${done ? 'bg-green-900/50 text-green-400 border border-green-800'
                            : active ? 'bg-red-600 text-white shadow-lg shadow-red-900/30'
                                : 'bg-gray-800 text-gray-500 border border-gray-700'}`}
                        >
                            {done ? <CheckCircle2 size={12} /> : <span>{idx}</span>}
                            {label}
                        </div>
                        {i < steps.length - 1 && <ChevronRight size={14} className="text-gray-700 shrink-0" />}
                    </div>
                )
            })}
        </div>
    )
}

function SelectedMovieSidebar({ movie, tmdbPoster }) {
    if (!movie) return null
    return (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 flex gap-3 items-start mb-4">
            {tmdbPoster ? (
                <img src={tmdbPoster} alt={movie.title} className="w-14 rounded-lg shrink-0 border border-gray-700" />
            ) : (
                <div className="w-14 h-20 bg-gray-800 rounded-lg shrink-0 border border-gray-700 flex items-center justify-center">
                    <Film size={18} className="text-gray-600" />
                </div>
            )}
            <div className="min-w-0">
                <p className="text-xs text-gray-500 mb-0.5">Selected Movie</p>
                <p className="text-white text-sm font-bold leading-tight line-clamp-2">{movie.title}</p>
                {movie.genre && <p className="text-gray-500 text-xs mt-1">{movie.genre}</p>}
                {movie.duration && <p className="text-gray-500 text-xs">{movie.duration} min</p>}
            </div>
        </div>
    )
}

function Section({ icon, title, done, summary, onEdit, children }) {
    const collapsed = done && summary
    return (
        <div className={`bg-gray-900 rounded-xl border transition-colors ${done ? 'border-green-900/50' : 'border-gray-800'}`}>
            <div className="flex items-center justify-between px-5 py-4">
                <div className="flex items-center gap-2">
                    <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${done ? 'bg-green-900/50 text-green-400' : 'bg-gray-800 text-gray-400'}`}>
                        {done ? <CheckCircle2 size={15} /> : icon}
                    </div>
                    <div>
                        <p className="text-sm font-semibold text-white">{title}</p>
                        {collapsed && <p className="text-xs text-gray-400 mt-0.5">{summary}</p>}
                    </div>
                </div>
                {collapsed && (
                    <button onClick={onEdit} className="text-xs text-red-400 hover:text-red-300 font-medium px-2 py-1 rounded hover:bg-red-950/30 transition-colors">
                        Change
                    </button>
                )}
            </div>
            {!collapsed && <div className="px-5 pb-5">{children}</div>}
        </div>
    )
}

function MoviePickCard({ movie, selected, onSelect }) {
    const [poster, setPoster] = useState(null)
    useEffect(() => {
        searchMovie(movie.title).then((t) => setPoster(posterUrl(t?.poster_path, 'w185')))
    }, [movie.title])

    return (
        <button
            onClick={onSelect}
            className={`relative rounded-xl overflow-hidden border-2 transition-all hover:scale-105 ${selected ? 'border-red-500 shadow-lg shadow-red-900/40' : 'border-transparent hover:border-gray-600'}`}
        >
            <div className="aspect-[2/3] bg-gray-800">
                {poster ? (
                    <img src={poster} alt={movie.title} className="w-full h-full object-cover" />
                ) : (
                    <div className="w-full h-full flex items-center justify-center">
                        <Film size={24} className="text-gray-600" />
                    </div>
                )}
            </div>
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 to-transparent" />
            <div className="absolute bottom-0 left-0 right-0 p-2">
                <p className="text-white text-xs font-bold line-clamp-2 leading-tight">{movie.title}</p>
            </div>
            {selected && (
                <div className="absolute top-2 right-2 w-5 h-5 bg-red-600 rounded-full flex items-center justify-center">
                    <CheckCircle2 size={12} className="text-white" />
                </div>
            )}
        </button>
    )
}

function SummaryRow({ label, value, highlight }) {
    return (
        <div className="flex justify-between gap-2">
            <span className="text-gray-500 shrink-0 text-xs">{label}</span>
            <span className={`text-right text-xs leading-relaxed ${value ? (highlight ? 'font-bold text-white' : 'text-gray-300') : 'text-gray-700'}`}>
        {value ?? '—'}
      </span>
        </div>
    )
}

export default function Book() {
    const { user } = useAuth()
    const navigate = useNavigate()

    const [movies, setMovies] = useState(null)
    const [theaters, setTheaters] = useState(null)
    const [screens, setScreens] = useState(null)
    const [showtimes, setShowtimes] = useState(null)
    const [seats, setSeats] = useState(null)

    const [selectedMovie, setSelectedMovie] = useState(null)
    const [selectedTheater, setSelectedTheater] = useState(null)
    const [selectedScreen, setSelectedScreen] = useState(null)
    const [selectedShowtime, setSelectedShowtime] = useState(null)

    // Multiple seats — stored as a Set of seatId for fast lookup
    const [selectedSeats, setSelectedSeats] = useState([]) // array of seat objects

    const [tmdbPoster, setTmdbPoster] = useState(null)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState(null)

    const step = !selectedMovie ? 1
        : !selectedTheater ? 2
            : !selectedScreen || !selectedShowtime ? 3
                : selectedSeats.length === 0 ? 4
                    : 5

    useEffect(() => {
        Promise.all([moviesApi.list(), theatersApi.list(), screensApi.list(), showtimesApi.list()])
            .then(([mv, th, sc, st]) => {
                setMovies(mv.filter((m) => m.status === 'NOW_SHOWING'))
                setTheaters(th)
                setScreens(sc)
                setShowtimes(st)
            })
            .catch(setError)
    }, [])

    useEffect(() => {
        if (!selectedMovie) { setTmdbPoster(null); return }
        searchMovie(selectedMovie.title).then((tmdb) => {
            setTmdbPoster(posterUrl(tmdb?.poster_path, 'w185'))
        })
    }, [selectedMovie])

    useEffect(() => {
        if (!selectedScreen) { setSeats(null); return }
        setSeats(null)
        setSelectedSeats([])
        seatsApi.byScreen(selectedScreen.screenId).then(setSeats).catch(setError)
    }, [selectedScreen])

    // Toggle seat selection
    function toggleSeat(seat) {
        if (seat.status !== 'AVAILABLE') return
        setSelectedSeats((prev) => {
            const exists = prev.find((s) => getSeatId(s) === getSeatId(seat))
            if (exists) return prev.filter((s) => getSeatId(s) !== getSeatId(seat))
            return [...prev, seat]
        })
    }

    // Confirm — create one booking per seat
    async function confirmBooking() {
        setBusy(true)
        setError(null)
        const failed = []
        const created = []

        try {
            for (const seat of selectedSeats) {
                try {
                    const booking = await bookingsApi.create({
                        userId: user.userId,
                        showtimeId: selectedShowtime.showtimeId,
                        seatId: getSeatId(seat),
                    })
                    created.push(booking)
                } catch (err) {
                    if (err.status === 409) {
                        // Seat was taken between selection and booking
                        failed.push(seat.seatNumber)
                    } else {
                        throw err
                    }
                }
            }

            if (failed.length > 0) {
                toast.error(`Seat(s) ${failed.join(', ')} were taken. Others booked successfully.`)
                // Reload seats to show updated availability
                seatsApi.byScreen(selectedScreen.screenId).then(setSeats)
                setSelectedSeats([])
            }

            if (created.length > 0) {
                toast.success(`${created.length} booking(s) created!`)
                navigate(`/bookings?created=${created[0].bookingId}`)
            }
        } catch (err) {
            setError(err)
        } finally {
            setBusy(false)
        }
    }

    if (!movies) return <Spinner />

    const filteredScreens = selectedTheater
        ? (screens ?? []).filter((s) => s.theaterId === selectedTheater.theaterId)
        : (screens ?? [])

    const filteredShowtimes = selectedScreen
        ? (showtimes ?? []).filter((s) => s.screenId === selectedScreen.screenId)
        : (showtimes ?? [])

    const available = seats?.filter((s) => s.status === 'AVAILABLE').length ?? 0
    const totalAmount = selectedSeats.reduce((sum, s) => sum + seatPrice(s), 0)
    const totalPrice = totalAmount.toFixed(2)
    const totalKHR = Math.round(totalAmount * 4100).toLocaleString()

    return (
        <div>
            <div className="mb-2">
                <h1 className="text-2xl font-bold text-white">Book Seats</h1>
                <p className="text-gray-500 text-sm mt-1">Select multiple seats — one ticket per seat</p>
            </div>

            <Steps current={step} />

            <div className="grid lg:grid-cols-3 gap-6">
                {/* Left: Steps */}
                <div className="lg:col-span-2 space-y-4">

                    {/* Step 1 — Movie */}
                    <Section
                        icon={<Film size={16} />}
                        title="1. Choose a Movie"
                        done={!!selectedMovie}
                        summary={selectedMovie?.title}
                        onEdit={() => { setSelectedMovie(null); setSelectedTheater(null); setSelectedScreen(null); setSelectedShowtime(null); setSelectedSeats([]) }}
                    >
                        {movies.length === 0 ? (
                            <p className="text-gray-500 text-sm">No movies currently showing.</p>
                        ) : (
                            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                                {movies.map((m) => (
                                    <MoviePickCard
                                        key={m.movieId}
                                        movie={m}
                                        selected={selectedMovie?.movieId === m.movieId}
                                        onSelect={() => { setSelectedMovie(m); setSelectedTheater(null); setSelectedScreen(null); setSelectedShowtime(null); setSelectedSeats([]) }}
                                    />
                                ))}
                            </div>
                        )}
                    </Section>

                    {/* Step 2 — Theater */}
                    {selectedMovie && (
                        <Section
                            icon={<Building2 size={16} />}
                            title="2. Choose a Theater"
                            done={!!selectedTheater}
                            summary={selectedTheater?.name}
                            onEdit={() => { setSelectedTheater(null); setSelectedScreen(null); setSelectedShowtime(null); setSelectedSeats([]) }}
                        >
                            {(theaters ?? []).length === 0 ? (
                                <p className="text-gray-500 text-sm">No theaters available.</p>
                            ) : (
                                <div className="grid gap-2">
                                    {(theaters ?? []).map((t) => (
                                        <button
                                            key={t.theaterId}
                                            onClick={() => { setSelectedTheater(t); setSelectedScreen(null); setSelectedShowtime(null); setSelectedSeats([]) }}
                                            className={`flex items-center gap-3 p-3 rounded-xl border text-left transition-all
                        ${selectedTheater?.theaterId === t.theaterId ? 'border-red-600 bg-red-950/30' : 'border-gray-700 bg-gray-800/50 hover:border-gray-600'}`}
                                        >
                                            <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${selectedTheater?.theaterId === t.theaterId ? 'bg-red-600' : 'bg-gray-700'}`}>
                                                <Building2 size={16} className="text-white" />
                                            </div>
                                            <div>
                                                <p className="text-sm font-semibold text-white">{t.name}</p>
                                                <p className="text-xs text-gray-400">{t.location}{t.address ? ` · ${t.address}` : ''}</p>
                                            </div>
                                            {selectedTheater?.theaterId === t.theaterId && <CheckCircle2 size={16} className="text-red-500 ml-auto shrink-0" />}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </Section>
                    )}

                    {/* Step 3 — Screen + Showtime */}
                    {selectedTheater && (
                        <Section
                            icon={<Monitor size={16} />}
                            title="3. Choose Screen & Showtime"
                            done={!!(selectedScreen && selectedShowtime)}
                            summary={selectedScreen && selectedShowtime ? `${selectedScreen.name} · ${selectedShowtime.showDate} ${selectedShowtime.startTime}` : null}
                            onEdit={() => { setSelectedScreen(null); setSelectedShowtime(null); setSelectedSeats([]) }}
                        >
                            <div className="space-y-4">
                                <div>
                                    <p className="text-xs text-gray-400 font-medium mb-2 uppercase tracking-wide">Screen</p>
                                    {filteredScreens.length === 0 ? (
                                        <p className="text-gray-500 text-sm">No screens for this theater.</p>
                                    ) : (
                                        <div className="grid grid-cols-2 gap-2">
                                            {filteredScreens.map((s) => (
                                                <button
                                                    key={s.screenId}
                                                    onClick={() => { setSelectedScreen(s); setSelectedShowtime(null); setSelectedSeats([]) }}
                                                    className={`p-3 rounded-xl border text-left transition-all
                            ${selectedScreen?.screenId === s.screenId ? 'border-red-600 bg-red-950/30' : 'border-gray-700 bg-gray-800/50 hover:border-gray-600'}`}
                                                >
                                                    <p className="text-sm font-semibold text-white">{s.name}</p>
                                                    <p className="text-xs text-gray-400">{s.screenType} · {s.capacity} seats</p>
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                {selectedScreen && (
                                    <div>
                                        <p className="text-xs text-gray-400 font-medium mb-2 uppercase tracking-wide">Showtime</p>
                                        {filteredShowtimes.length === 0 ? (
                                            <p className="text-gray-500 text-sm">No showtimes for this screen.</p>
                                        ) : (
                                            <div className="flex flex-wrap gap-2">
                                                {filteredShowtimes.map((s) => (
                                                    <button
                                                        key={s.showtimeId}
                                                        onClick={() => { setSelectedShowtime(s); setSelectedSeats([]) }}
                                                        className={`flex items-center gap-1.5 px-3 py-2 rounded-lg border text-sm transition-all
                              ${selectedShowtime?.showtimeId === s.showtimeId ? 'border-red-600 bg-red-950/30 text-white' : 'border-gray-700 bg-gray-800/50 text-gray-300 hover:border-gray-600'}`}
                                                    >
                                                        <Clock size={13} />
                                                        {s.showDate} · {s.startTime}
                                                    </button>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        </Section>
                    )}

                    {/* Step 4 — Seats (multi-select) */}
                    {selectedScreen && selectedShowtime && (
                        <Section
                            icon={<Monitor size={16} />}
                            title="4. Choose Seats"
                            done={selectedSeats.length > 0}
                            summary={selectedSeats.length > 0 ? `${selectedSeats.length} seat(s): ${selectedSeats.map(s => s.seatNumber).join(', ')}` : null}
                            onEdit={() => setSelectedSeats([])}
                        >
                            {!seats ? (
                                <Spinner label="Loading seats..." />
                            ) : (
                                <>
                                    {/* Hint */}
                                    <p className="text-xs text-gray-500 mb-4">
                                        Click seats to select/deselect. You can select multiple seats.
                                    </p>

                                    {/* Screen label */}
                                    <div className="mb-5">
                                        <div className="w-2/3 mx-auto h-1 bg-gradient-to-r from-transparent via-gray-500 to-transparent rounded-full mb-1" />
                                        <p className="text-center text-xs text-gray-600 tracking-widest uppercase">Screen</p>
                                    </div>

                                    {/* Legend */}
                                    <div className="flex items-center justify-center gap-5 mb-5 text-xs text-gray-400 flex-wrap">
                                        <span className="flex items-center gap-1.5"><div className="w-4 h-4 rounded border-2 border-green-500 bg-green-950/50" /> Available ({available})</span>
                                        <span className="flex items-center gap-1.5"><div className="w-4 h-4 rounded bg-red-600 border-2 border-red-500" /> Selected ({selectedSeats.length})</span>
                                        <span className="flex items-center gap-1.5"><div className="w-4 h-4 rounded bg-gray-700 border-2 border-gray-600" /> Taken</span>
                                    </div>

                                    {seats.length === 0 ? (
                                        <p className="text-center text-gray-500 text-sm py-6">No seats for this screen yet.</p>
                                    ) : (
                                        <div className="grid grid-cols-8 sm:grid-cols-10 gap-1.5 max-w-lg mx-auto">
                                            {seats.map((seat) => {
                                                const free = seat.status === 'AVAILABLE'
                                                const isSelected = selectedSeats.some((s) => getSeatId(s) === getSeatId(seat))
                                                return (
                                                    <button
                                                        key={getSeatId(seat)}
                                                        disabled={!free}
                                                        onClick={() => toggleSeat(seat)}
                                                        title={`${seat.seatNumber} — ${seat.seatType} — ${seat.status}`}
                                                        className={`aspect-square rounded text-xs font-bold border-2 transition-all duration-150
                              ${isSelected
                                                            ? 'bg-red-600 border-red-500 text-white scale-110 shadow-lg shadow-red-900/50'
                                                            : free
                                                                ? 'bg-green-950/50 border-green-700 text-green-400 hover:bg-green-600 hover:text-white hover:scale-105'
                                                                : 'bg-gray-800 border-gray-700 text-gray-600 cursor-not-allowed line-through'
                                                        }`}
                                                    >
                                                        {seat.seatNumber}
                                                    </button>
                                                )
                                            })}
                                        </div>
                                    )}

                                    {/* Selected seats chips */}
                                    {selectedSeats.length > 0 && (
                                        <div className="mt-5 flex flex-wrap gap-2">
                                            {selectedSeats.map((s) => (
                                                <div key={getSeatId(s)} className="flex items-center gap-1.5 bg-red-950/50 border border-red-800 text-red-300 text-xs font-medium px-2.5 py-1 rounded-full">
                                                    {s.seatNumber}
                                                    <button onClick={() => toggleSeat(s)} className="hover:text-white transition-colors">
                                                        <X size={11} />
                                                    </button>
                                                </div>
                                            ))}
                                            <button
                                                onClick={() => setSelectedSeats([])}
                                                className="text-xs text-gray-500 hover:text-gray-300 px-2.5 py-1 rounded-full border border-gray-700 hover:border-gray-600 transition-colors"
                                            >
                                                Clear all
                                            </button>
                                        </div>
                                    )}
                                </>
                            )}
                        </Section>
                    )}

                    <ErrorBox error={error} />
                </div>

                {/* Right: Summary */}
                <div className="lg:col-span-1">
                    <div className="sticky top-20 space-y-4">
                        <SelectedMovieSidebar movie={selectedMovie} tmdbPoster={tmdbPoster} />

                        <div className="bg-gray-900 border border-gray-800 rounded-xl p-4">
                            <h3 className="text-sm font-bold text-white mb-4">Booking Summary</h3>
                            <div className="space-y-2.5 text-sm">
                                <SummaryRow label="Movie" value={selectedMovie?.title} />
                                <SummaryRow label="Theater" value={selectedTheater?.name} />
                                <SummaryRow label="Screen" value={selectedScreen?.name} />
                                <SummaryRow label="Showtime" value={selectedShowtime ? `${selectedShowtime.showDate} · ${selectedShowtime.startTime}` : null} />
                                <SummaryRow
                                    label="Seats"
                                    value={selectedSeats.length > 0 ? selectedSeats.map(s => s.seatNumber).join(', ') : null}
                                    highlight
                                />

                                <div className="border-t border-gray-800 pt-3 mt-1 space-y-1.5">
                                    <div className="flex justify-between text-xs text-gray-500">
                                        <span>{selectedSeats.length} seat(s)</span>
                                        <span>${totalPrice}</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-gray-400 text-xs">Total</span>
                                        <span className="font-black text-white text-lg">${totalPrice}</span>
                                    </div>
                                    <p className="text-xs text-gray-600 text-right">≈ {totalKHR} KHR</p>
                                </div>
                            </div>

                            <button
                                onClick={confirmBooking}
                                disabled={step < 5 || busy}
                                className="w-full mt-5 flex items-center justify-center gap-2 bg-red-600 hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed text-white font-bold py-3 rounded-xl text-sm transition-all hover:scale-[1.02] shadow-lg shadow-red-900/30"
                            >
                                <CheckCircle2 size={16} />
                                {busy ? 'Booking...'
                                    : step < 5 ? 'Complete all steps'
                                        : `Confirm ${selectedSeats.length} Seat(s)`}
                            </button>

                            {step < 5 && (
                                <p className="text-center text-xs text-gray-600 mt-2">
                                    {selectedSeats.length === 0 && selectedScreen && selectedShowtime
                                        ? 'Select at least one seat'
                                        : `${5 - step} step(s) remaining`}
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}
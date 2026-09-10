import { useCallback, useEffect, useState } from 'react'
import {
    admin as adminApi,
    movies as moviesApi,
    theaters as theatersApi,
    screens as screensApi,
    showtimes as showtimesApi,
    seats as seatsApi,
} from '../api/endpoints'
import { ErrorBox, Spinner } from '../components/Common'
import { Film, Building2, Users, Plus, Monitor, Clock, Armchair, Activity } from 'lucide-react'
import toast from 'react-hot-toast'
import HealthTab from '../components/HealthTab'

const inp = 'w-full px-3 py-2.5 bg-gray-800 border border-gray-700 rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-transparent transition'

function Field({ label, hint, children }) {
    return (
        <div>
            <label className="block text-sm font-medium text-gray-300 mb-1.5">
                {label}
                {hint && <span className="text-gray-600 font-normal ml-1 text-xs">({hint})</span>}
            </label>
            {children}
        </div>
    )
}

export default function Admin() {
    const [users, setUsers] = useState(null)
    const [theaters, setTheaters] = useState([])
    const [screens, setScreens] = useState([])
    const [movies, setMovies] = useState([])
    const [error, setError] = useState(null)
    const [activeTab, setActiveTab] = useState('movies')

    // Form states
    const [movie, setMovie] = useState({ title: '', genre: '', duration: '', rating: '', language: '', status: 'NOW_SHOWING' })
    const [theater, setTheater] = useState({ name: '', location: '', address: '' })
    const [screen, setScreen] = useState({ name: '', screenType: 'STANDARD', capacity: '', theaterId: '' })
    const [showtime, setShowtime] = useState({ movieId: '', screenId: '', showDate: '', startTime: '', endTime: '' })
    const [seat, setSeat] = useState({ screenId: '', seatNumber: '', seatType: 'STANDARD', rows: '', cols: '' })
    const [bulkMode, setBulkMode] = useState(false)

    // Busy states
    const [busy, setBusy] = useState(false)

    const load = useCallback(async () => {
        try {
            const [u, th, sc, mv] = await Promise.all([
                adminApi.users(),
                theatersApi.list(),
                screensApi.list(),
                moviesApi.list(),
            ])
            setUsers(u)
            setTheaters(th)
            setScreens(sc)
            setMovies(mv)
        } catch (err) {
            setError(err)
        }
    }, [])

    useEffect(() => { load() }, [load])

    // ── Handlers ──

    async function addMovie(e) {
        e.preventDefault(); setBusy(true); setError(null)
        try {
            const created = await moviesApi.create({
                title: movie.title,
                genre: movie.genre || null,
                duration: movie.duration ? Number(movie.duration) : null,
                rating: movie.rating ? Number(movie.rating) : null,
                language: movie.language || null,
                status: movie.status,
            })
            toast.success(`"${created.title}" added!`)
            setMovie({ title: '', genre: '', duration: '', rating: '', language: '', status: 'NOW_SHOWING' })
            load()
        } catch (err) { setError(err); toast.error('Failed to add movie.') }
        finally { setBusy(false) }
    }

    async function addTheater(e) {
        e.preventDefault(); setBusy(true); setError(null)
        try {
            const created = await theatersApi.create({ name: theater.name, location: theater.location, address: theater.address || null })
            toast.success(`Theater "${created.name}" added!`)
            setTheater({ name: '', location: '', address: '' })
            load()
        } catch (err) { setError(err); toast.error('Failed to add theater.') }
        finally { setBusy(false) }
    }

    async function addScreen(e) {
        e.preventDefault(); setBusy(true); setError(null)
        try {
            const created = await screensApi.create({
                name: screen.name,
                screenType: screen.screenType,
                capacity: Number(screen.capacity),
                theaterId: Number(screen.theaterId),
            })
            toast.success(`Screen "${created.name}" added!`)
            setScreen({ name: '', screenType: 'STANDARD', capacity: '', theaterId: screen.theaterId })
            load()
        } catch (err) { setError(err); toast.error('Failed to add screen.') }
        finally { setBusy(false) }
    }

    async function addShowtime(e) {
        e.preventDefault(); setBusy(true); setError(null)
        try {
            const created = await showtimesApi.create({
                movieId: Number(showtime.movieId),
                screenId: Number(showtime.screenId),
                showDate: showtime.showDate,
                startTime: showtime.startTime,
                endTime: showtime.endTime,
            })
            toast.success(`Showtime added for ${showtime.showDate}!`)
            setShowtime({ ...showtime, showDate: '', startTime: '', endTime: '' })
            load()
        } catch (err) { setError(err); toast.error('Failed to add showtime.') }
        finally { setBusy(false) }
    }

    async function addSeats(e) {
        e.preventDefault(); setBusy(true); setError(null)
        try {
            if (bulkMode) {
                // Bulk: generate seats like A1, A2... B1, B2...
                const rows = seat.rows.toUpperCase().split('').filter(c => c >= 'A' && c <= 'Z')
                const cols = Number(seat.cols)
                if (rows.length === 0 || !cols) { toast.error('Invalid rows or columns'); setBusy(false); return }
                let count = 0
                for (const row of rows) {
                    for (let col = 1; col <= cols; col++) {
                        await seatsApi.create({
                            screenId: Number(seat.screenId),
                            seatNumber: `${row}${col}`,
                            seatType: seat.seatType,
                            status: 'AVAILABLE',
                        })
                        count++
                    }
                }
                toast.success(`${count} seats added!`)
            } else {
                await seatsApi.create({
                    screenId: Number(seat.screenId),
                    seatNumber: seat.seatNumber,
                    seatType: seat.seatType,
                    status: 'AVAILABLE',
                })
                toast.success(`Seat ${seat.seatNumber} added!`)
                setSeat({ ...seat, seatNumber: '' })
            }
            load()
        } catch (err) { setError(err); toast.error('Failed to add seat(s).') }
        finally { setBusy(false) }
    }

    const tabs = [
        { id: 'movies',    label: 'Movies',    icon: <Film size={14} /> },
        { id: 'theaters',  label: 'Theaters',  icon: <Building2 size={14} /> },
        { id: 'screens',   label: 'Screens',   icon: <Monitor size={14} /> },
        { id: 'showtimes', label: 'Showtimes', icon: <Clock size={14} /> },
        { id: 'seats',     label: 'Seats',     icon: <Armchair size={14} /> },
        { id: 'users',     label: 'Users',     icon: <Users size={14} /> },
        { id: 'health',    label: 'Health',    icon: <Activity size={14} /> },
    ]

    return (
        <div>
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-white">Admin Dashboard</h1>
                <p className="text-gray-500 text-sm mt-1">Manage your cinema data</p>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-6">
                {[
                    { label: 'Users',     value: users?.length,    icon: <Users size={18} />,    color: 'text-blue-400 bg-blue-950/50 border-blue-900' },
                    { label: 'Movies',    value: movies?.length,   icon: <Film size={18} />,     color: 'text-red-400 bg-red-950/50 border-red-900' },
                    { label: 'Theaters',  value: theaters?.length, icon: <Building2 size={18} />,color: 'text-green-400 bg-green-950/50 border-green-900' },
                    { label: 'Screens',   value: screens?.length,  icon: <Monitor size={18} />,  color: 'text-purple-400 bg-purple-950/50 border-purple-900' },
                    { label: 'Showtimes', value: '—',              icon: <Clock size={18} />,    color: 'text-yellow-400 bg-yellow-950/50 border-yellow-900' },
                ].map((s) => (
                    <div key={s.label} className="bg-gray-900 rounded-xl border border-gray-800 p-3 flex items-center gap-2.5">
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center border shrink-0 ${s.color}`}>{s.icon}</div>
                        <div>
                            <p className="text-lg font-bold text-white">{s.value ?? '...'}</p>
                            <p className="text-xs text-gray-500">{s.label}</p>
                        </div>
                    </div>
                ))}
            </div>

            <ErrorBox error={error} />

            {/* Tabs */}
            <div className="flex gap-1 bg-gray-800/50 rounded-xl p-1 mb-6 border border-gray-700 flex-wrap">
                {tabs.map((t) => (
                    <button key={t.id} onClick={() => setActiveTab(t.id)}
                            className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold transition-all ${
                                activeTab === t.id ? 'bg-gray-700 text-white shadow-sm' : 'text-gray-500 hover:text-gray-300'}`}>
                        {t.icon}{t.label}
                    </button>
                ))}
            </div>

            {/* ── MOVIES ── */}
            {activeTab === 'movies' && (
                <div className="bg-gray-900 rounded-xl border border-gray-800 p-6 max-w-lg">
                    <h2 className="text-base font-semibold text-white mb-5 flex items-center gap-2">
                        <Film size={16} className="text-red-400" /> Add New Movie
                    </h2>
                    <form onSubmit={addMovie} className="space-y-4">
                        <Field label="Title *"><input value={movie.title} onChange={(e) => setMovie({ ...movie, title: e.target.value })} required placeholder="Movie title" className={inp} /></Field>
                        <div className="grid grid-cols-2 gap-3">
                            <Field label="Genre"><input value={movie.genre} onChange={(e) => setMovie({ ...movie, genre: e.target.value })} placeholder="Action, Drama..." className={inp} /></Field>
                            <Field label="Language"><input value={movie.language} onChange={(e) => setMovie({ ...movie, language: e.target.value })} placeholder="English..." className={inp} /></Field>
                        </div>
                        <div className="grid grid-cols-2 gap-3">
                            <Field label="Duration" hint="minutes"><input type="number" min="1" value={movie.duration} onChange={(e) => setMovie({ ...movie, duration: e.target.value })} placeholder="120" className={inp} /></Field>
                            <Field label="Rating" hint="0–10"><input type="number" step="0.1" min="0" max="10" value={movie.rating} onChange={(e) => setMovie({ ...movie, rating: e.target.value })} placeholder="8.5" className={inp} /></Field>
                        </div>
                        <Field label="Status">
                            <select value={movie.status} onChange={(e) => setMovie({ ...movie, status: e.target.value })} className={inp}>
                                <option value="NOW_SHOWING">Now Showing</option>
                                <option value="COMING_SOON">Coming Soon</option>
                                <option value="ENDED">Ended</option>
                            </select>
                        </Field>
                        <button type="submit" disabled={busy} className="w-full flex items-center justify-center gap-2 bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-bold py-2.5 rounded-lg text-sm transition-colors">
                            <Plus size={15} />{busy ? 'Adding...' : 'Add Movie'}
                        </button>
                    </form>
                </div>
            )}

            {/* ── THEATERS ── */}
            {activeTab === 'theaters' && (
                <div className="grid md:grid-cols-2 gap-6">
                    <div className="bg-gray-900 rounded-xl border border-gray-800 p-6">
                        <h2 className="text-base font-semibold text-white mb-5 flex items-center gap-2">
                            <Building2 size={16} className="text-green-400" /> Add New Theater
                        </h2>
                        <form onSubmit={addTheater} className="space-y-4">
                            <Field label="Name *"><input value={theater.name} onChange={(e) => setTheater({ ...theater, name: e.target.value })} required placeholder="Sabay Cineplex" className={inp} /></Field>
                            <Field label="Location *"><input value={theater.location} onChange={(e) => setTheater({ ...theater, location: e.target.value })} required placeholder="Phnom Penh" className={inp} /></Field>
                            <Field label="Address"><input value={theater.address} onChange={(e) => setTheater({ ...theater, address: e.target.value })} placeholder="Street 123..." className={inp} /></Field>
                            <button type="submit" disabled={busy} className="w-full flex items-center justify-center gap-2 bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white font-bold py-2.5 rounded-lg text-sm transition-colors">
                                <Plus size={15} />{busy ? 'Adding...' : 'Add Theater'}
                            </button>
                        </form>
                    </div>

                    {/* Theater list */}
                    <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
                        <div className="px-4 py-3 border-b border-gray-800">
                            <h3 className="text-sm font-semibold text-white">Existing Theaters</h3>
                        </div>
                        {theaters.length === 0 ? (
                            <p className="text-gray-500 text-sm p-4">No theaters yet.</p>
                        ) : (
                            <div className="divide-y divide-gray-800">
                                {theaters.map((t) => (
                                    <div key={t.theaterId} className="px-4 py-3">
                                        <p className="text-sm font-medium text-white">{t.name}</p>
                                        <p className="text-xs text-gray-500">{t.location}{t.address ? ` · ${t.address}` : ''}</p>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* ── SCREENS ── */}
            {activeTab === 'screens' && (
                <div className="grid md:grid-cols-2 gap-6">
                    <div className="bg-gray-900 rounded-xl border border-gray-800 p-6">
                        <h2 className="text-base font-semibold text-white mb-1 flex items-center gap-2">
                            <Monitor size={16} className="text-purple-400" /> Add New Screen
                        </h2>
                        <p className="text-xs text-gray-500 mb-5">A screen belongs to a theater. Add theaters first.</p>
                        <form onSubmit={addScreen} className="space-y-4">
                            <Field label="Theater *">
                                <select value={screen.theaterId} onChange={(e) => setScreen({ ...screen, theaterId: e.target.value })} required className={inp}>
                                    <option value="">Select a theater...</option>
                                    {theaters.map((t) => <option key={t.theaterId} value={t.theaterId}>{t.name}</option>)}
                                </select>
                            </Field>
                            <Field label="Screen Name *"><input value={screen.name} onChange={(e) => setScreen({ ...screen, name: e.target.value })} required placeholder="Screen 1" className={inp} /></Field>
                            <div className="grid grid-cols-2 gap-3">
                                <Field label="Type">
                                    <select value={screen.screenType} onChange={(e) => setScreen({ ...screen, screenType: e.target.value })} className={inp}>
                                        <option value="STANDARD">Standard</option>
                                        <option value="IMAX">IMAX</option>
                                        <option value="VIP">VIP</option>
                                        <option value="4DX">4DX</option>
                                    </select>
                                </Field>
                                <Field label="Capacity *"><input type="number" min="1" value={screen.capacity} onChange={(e) => setScreen({ ...screen, capacity: e.target.value })} required placeholder="100" className={inp} /></Field>
                            </div>
                            <button type="submit" disabled={busy} className="w-full flex items-center justify-center gap-2 bg-purple-600 hover:bg-purple-700 disabled:opacity-50 text-white font-bold py-2.5 rounded-lg text-sm transition-colors">
                                <Plus size={15} />{busy ? 'Adding...' : 'Add Screen'}
                            </button>
                        </form>
                    </div>

                    {/* Screen list */}
                    <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
                        <div className="px-4 py-3 border-b border-gray-800">
                            <h3 className="text-sm font-semibold text-white">Existing Screens</h3>
                        </div>
                        {screens.length === 0 ? (
                            <p className="text-gray-500 text-sm p-4">No screens yet.</p>
                        ) : (
                            <div className="divide-y divide-gray-800">
                                {screens.map((s) => {
                                    const theater = theaters.find((t) => t.theaterId === s.theaterId)
                                    return (
                                        <div key={s.screenId} className="px-4 py-3">
                                            <p className="text-sm font-medium text-white">{s.name} <span className="text-xs text-purple-400 font-normal">{s.screenType}</span></p>
                                            <p className="text-xs text-gray-500">{theater?.name ?? `Theater #${s.theaterId}`} · {s.capacity} seats</p>
                                        </div>
                                    )
                                })}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* ── SHOWTIMES ── */}
            {activeTab === 'showtimes' && (
                <div className="bg-gray-900 rounded-xl border border-gray-800 p-6 max-w-lg">
                    <h2 className="text-base font-semibold text-white mb-1 flex items-center gap-2">
                        <Clock size={16} className="text-yellow-400" /> Add New Showtime
                    </h2>
                    <p className="text-xs text-gray-500 mb-5">A showtime links a movie to a screen at a specific time.</p>
                    <form onSubmit={addShowtime} className="space-y-4">
                        <Field label="Movie *">
                            <select value={showtime.movieId} onChange={(e) => setShowtime({ ...showtime, movieId: e.target.value })} required className={inp}>
                                <option value="">Select a movie...</option>
                                {movies.map((m) => <option key={m.movieId} value={m.movieId}>{m.title}</option>)}
                            </select>
                        </Field>
                        <Field label="Screen *">
                            <select value={showtime.screenId} onChange={(e) => setShowtime({ ...showtime, screenId: e.target.value })} required className={inp}>
                                <option value="">Select a screen...</option>
                                {screens.map((s) => {
                                    const theater = theaters.find((t) => t.theaterId === s.theaterId)
                                    return <option key={s.screenId} value={s.screenId}>{theater?.name ?? ''} — {s.name} ({s.screenType})</option>
                                })}
                            </select>
                        </Field>
                        <Field label="Show Date *"><input type="date" value={showtime.showDate} onChange={(e) => setShowtime({ ...showtime, showDate: e.target.value })} required className={inp} /></Field>
                        <div className="grid grid-cols-2 gap-3">
                            <Field label="Start Time *"><input type="time" value={showtime.startTime} onChange={(e) => setShowtime({ ...showtime, startTime: e.target.value })} required className={inp} /></Field>
                            <Field label="End Time *"><input type="time" value={showtime.endTime} onChange={(e) => setShowtime({ ...showtime, endTime: e.target.value })} required className={inp} /></Field>
                        </div>
                        <button type="submit" disabled={busy} className="w-full flex items-center justify-center gap-2 bg-yellow-600 hover:bg-yellow-700 disabled:opacity-50 text-white font-bold py-2.5 rounded-lg text-sm transition-colors">
                            <Plus size={15} />{busy ? 'Adding...' : 'Add Showtime'}
                        </button>
                    </form>
                </div>
            )}

            {/* ── SEATS ── */}
            {activeTab === 'seats' && (
                <div className="bg-gray-900 rounded-xl border border-gray-800 p-6 max-w-lg">
                    <h2 className="text-base font-semibold text-white mb-1 flex items-center gap-2">
                        <Armchair size={16} className="text-orange-400" /> Add Seats
                    </h2>
                    <p className="text-xs text-gray-500 mb-4">Add seats to a screen individually or in bulk.</p>

                    {/* Bulk toggle */}
                    <div className="flex gap-2 mb-5">
                        <button onClick={() => setBulkMode(false)}
                                className={`flex-1 py-2 rounded-lg text-xs font-semibold border transition-all ${!bulkMode ? 'bg-gray-700 text-white border-gray-600' : 'text-gray-500 border-gray-700 hover:text-gray-300'}`}>
                            Single Seat
                        </button>
                        <button onClick={() => setBulkMode(true)}
                                className={`flex-1 py-2 rounded-lg text-xs font-semibold border transition-all ${bulkMode ? 'bg-gray-700 text-white border-gray-600' : 'text-gray-500 border-gray-700 hover:text-gray-300'}`}>
                            Bulk Generate
                        </button>
                    </div>

                    <form onSubmit={addSeats} className="space-y-4">
                        <Field label="Screen *">
                            <select value={seat.screenId} onChange={(e) => setSeat({ ...seat, screenId: e.target.value })} required className={inp}>
                                <option value="">Select a screen...</option>
                                {screens.map((s) => {
                                    const theater = theaters.find((t) => t.theaterId === s.theaterId)
                                    return <option key={s.screenId} value={s.screenId}>{theater?.name ?? ''} — {s.name}</option>
                                })}
                            </select>
                        </Field>

                        <Field label="Seat Type">
                            <select value={seat.seatType} onChange={(e) => setSeat({ ...seat, seatType: e.target.value })} className={inp}>
                                <option value="STANDARD">Standard</option>
                                <option value="VIP">VIP</option>
                                <option value="COUPLE">Couple</option>
                            </select>
                        </Field>

                        {!bulkMode ? (
                            <Field label="Seat Number *" hint="e.g. A1, B5">
                                <input value={seat.seatNumber} onChange={(e) => setSeat({ ...seat, seatNumber: e.target.value })} required placeholder="A1" className={inp} />
                            </Field>
                        ) : (
                            <>
                                <Field label="Rows *" hint="e.g. ABCDE for 5 rows">
                                    <input value={seat.rows} onChange={(e) => setSeat({ ...seat, rows: e.target.value })} required placeholder="ABCDEFGH" className={inp} />
                                </Field>
                                <Field label="Seats per row *" hint="e.g. 10">
                                    <input type="number" min="1" max="30" value={seat.cols} onChange={(e) => setSeat({ ...seat, cols: e.target.value })} required placeholder="10" className={inp} />
                                </Field>
                                {seat.rows && seat.cols && (
                                    <div className="bg-gray-800 rounded-lg p-3 text-xs text-gray-400">
                                        Will generate <span className="text-white font-bold">{seat.rows.toUpperCase().replace(/[^A-Z]/g, '').length * Number(seat.cols || 0)}</span> seats
                                        ({seat.rows.toUpperCase().replace(/[^A-Z]/g, '').split('').join(', ')} × {seat.cols} cols)
                                    </div>
                                )}
                            </>
                        )}

                        <button type="submit" disabled={busy} className="w-full flex items-center justify-center gap-2 bg-orange-600 hover:bg-orange-700 disabled:opacity-50 text-white font-bold py-2.5 rounded-lg text-sm transition-colors">
                            <Plus size={15} />{busy ? 'Adding...' : bulkMode ? 'Generate Seats' : 'Add Seat'}
                        </button>
                    </form>
                </div>
            )}

            {/* ── HEALTH ── */}
            {activeTab === 'health' && <HealthTab />}

            {/* ── USERS ── */}
            {activeTab === 'users' && (
                <div className="bg-gray-900 rounded-xl border border-gray-800 overflow-hidden">
                    <div className="px-5 py-4 border-b border-gray-800 flex items-center justify-between">
                        <h2 className="text-base font-semibold text-white">All Users</h2>
                        <span className="text-xs text-gray-500">{users?.length ?? 0} total</span>
                    </div>
                    {!users ? <div className="p-6"><Spinner /></div> : (
                        <table className="w-full">
                            <thead className="bg-gray-800/50">
                            <tr>{['#', 'Name', 'Email', 'Phone'].map((h) => (
                                <th key={h} className="text-left text-xs font-medium text-gray-500 px-5 py-3">{h}</th>
                            ))}</tr>
                            </thead>
                            <tbody className="divide-y divide-gray-800">
                            {users.map((u) => (
                                <tr key={u.userId} className="hover:bg-gray-800/50 transition-colors">
                                    <td className="px-5 py-3 text-sm text-gray-600">{u.userId}</td>
                                    <td className="px-5 py-3">
                                        <div className="flex items-center gap-2">
                                            <div className="w-7 h-7 bg-red-950/50 rounded-full flex items-center justify-center text-xs font-bold text-red-400 border border-red-900">
                                                {u.name?.[0]?.toUpperCase()}
                                            </div>
                                            <span className="text-sm font-medium text-gray-200">{u.name}</span>
                                        </div>
                                    </td>
                                    <td className="px-5 py-3 text-sm text-gray-400">{u.email}</td>
                                    <td className="px-5 py-3 text-sm text-gray-500">{u.phone ?? '—'}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    )}
                </div>
            )}
        </div>
    )
}
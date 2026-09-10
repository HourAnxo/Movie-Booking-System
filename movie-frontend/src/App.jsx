import { BrowserRouter, Link, NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { RequireAdmin, RequireAuth } from './components/Common'
import Admin from './pages/Admin'
import Book from './pages/Book'
import Bookings from './pages/Bookings'
import Login from './pages/Login'
import Movies from './pages/Movies'
import Register from './pages/Register'
import { Toaster } from 'react-hot-toast'
import { Film, Ticket, BookOpen, LayoutDashboard } from 'lucide-react'

function Nav() {
    const { user, isAuthenticated, isAdmin, logout } = useAuth()

    return (
        <header className="fixed top-0 left-0 right-0 z-50 transition-all">
            {/* Gradient so nav fades into hero */}
            <div className="absolute inset-0 bg-gradient-to-b from-black/80 to-transparent pointer-events-none" />
            <div className="relative max-w-7xl mx-auto px-6 md:px-12 h-16 flex items-center gap-6">
                {/* Brand */}
                <Link to="/" className="flex items-center gap-2 font-black text-xl text-red-500 shrink-0">
                    <Film size={22} />
                    CineBook
                </Link>

                {/* Nav links */}
                <nav className="flex gap-1 flex-1">
                    <NavLink to="/" end className={({ isActive }) =>
                        `px-3 py-1.5 rounded text-sm font-medium transition-colors ${isActive ? 'text-white' : 'text-gray-400 hover:text-white'}`
                    }>Movies</NavLink>
                    {isAuthenticated && (
                        <NavLink to="/book" className={({ isActive }) =>
                            `px-3 py-1.5 rounded text-sm font-medium transition-colors ${isActive ? 'text-white' : 'text-gray-400 hover:text-white'}`
                        }>
                            <span className="flex items-center gap-1.5"><Ticket size={14} />Book</span>
                        </NavLink>
                    )}
                    {isAuthenticated && (
                        <NavLink to="/bookings" className={({ isActive }) =>
                            `px-3 py-1.5 rounded text-sm font-medium transition-colors ${isActive ? 'text-white' : 'text-gray-400 hover:text-white'}`
                        }>
                            <span className="flex items-center gap-1.5"><BookOpen size={14} />My Bookings</span>
                        </NavLink>
                    )}
                    {isAdmin && (
                        <NavLink to="/admin" className={({ isActive }) =>
                            `px-3 py-1.5 rounded text-sm font-medium transition-colors ${isActive ? 'text-white' : 'text-gray-400 hover:text-white'}`
                        }>
                            <span className="flex items-center gap-1.5"><LayoutDashboard size={14} />Admin</span>
                        </NavLink>
                    )}
                </nav>

                {/* Auth */}
                <div className="flex items-center gap-3 shrink-0">
                    {isAuthenticated ? (
                        <>
                            <div className="flex items-center gap-2">
                                <div className="w-8 h-8 rounded-full bg-red-600 text-white flex items-center justify-center text-sm font-bold">
                                    {user.username?.[0]?.toUpperCase()}
                                </div>
                                <span className="hidden sm:block text-sm text-gray-300 font-medium">{user.username}</span>
                            </div>
                            <button
                                onClick={logout}
                                className="text-sm text-gray-400 hover:text-white transition-colors px-3 py-1.5 rounded border border-white/20 hover:border-white/40"
                            >
                                Sign out
                            </button>
                        </>
                    ) : (
                        <>
                            <Link to="/login" className="text-sm text-gray-300 hover:text-white font-medium transition-colors">
                                Sign in
                            </Link>
                            <Link to="/register" className="text-sm bg-red-600 hover:bg-red-700 text-white font-bold px-4 py-1.5 rounded-lg transition-colors">
                                Register
                            </Link>
                        </>
                    )}
                </div>
            </div>
        </header>
    )
}

export default function App() {
    return (
        <AuthProvider>
            <BrowserRouter>
                <div className="min-h-screen bg-[#141414]">
                    <Nav />
                    <Routes>
                        {/* Movies page: full bleed (no padding — hero needs to be edge-to-edge) */}
                        <Route path="/" element={<Movies />} />

                        {/* Other pages: padded below fixed nav */}
                        <Route path="/login" element={<PageShell><Login /></PageShell>} />
                        <Route path="/register" element={<PageShell><Register /></PageShell>} />
                        <Route path="/book" element={<RequireAuth><PageShell><Book /></PageShell></RequireAuth>} />
                        <Route path="/bookings" element={<RequireAuth><PageShell><Bookings /></PageShell></RequireAuth>} />
                        <Route path="/admin" element={<RequireAdmin><PageShell><Admin /></PageShell></RequireAdmin>} />
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </div>
                <Toaster
                    position="top-right"
                    toastOptions={{
                        style: { background: '#1f2937', color: '#f9fafb', borderRadius: '10px', fontSize: '14px', border: '1px solid #374151' },
                    }}
                />
            </BrowserRouter>
        </AuthProvider>
    )
}

function PageShell({ children }) {
    return (
        <div className="pt-16 min-h-screen bg-[#141414]">
            <div className="max-w-6xl mx-auto px-6 md:px-12 py-10">{children}</div>
        </div>
    )
}
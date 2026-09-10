import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { searchMovie, posterUrl, backdropUrl } from '../api/useTMDB'
import { useAuth } from '../auth/useAuth'
import { X, Star, Clock, Globe, Ticket, Film } from 'lucide-react'

export default function MovieModal({ movie, onClose }) {
    const [tmdb, setTmdb] = useState(null)
    const { isAuthenticated } = useAuth()
    const navigate = useNavigate()

    useEffect(() => {
        searchMovie(movie.title).then(setTmdb)
        // Lock body scroll when modal is open
        document.body.style.overflow = 'hidden'
        return () => { document.body.style.overflow = '' }
    }, [movie.title])

    // Close on backdrop click
    function handleBackdrop(e) {
        if (e.target === e.currentTarget) onClose()
    }

    // Close on Escape key
    useEffect(() => {
        function onKey(e) { if (e.key === 'Escape') onClose() }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [onClose])

    const poster = posterUrl(tmdb?.poster_path, 'w342')
    const backdrop = backdropUrl(tmdb?.backdrop_path, 'w1280')

    const STATUS = {
        NOW_SHOWING: { label: 'Now Showing', cls: 'bg-red-600 text-white' },
        COMING_SOON: { label: 'Coming Soon', cls: 'bg-yellow-500 text-black' },
        ENDED: { label: 'Ended', cls: 'bg-gray-600 text-white' },
    }
    const status = STATUS[movie.status] ?? { label: movie.status, cls: 'bg-gray-600 text-white' }

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm"
            onClick={handleBackdrop}
        >
            <div className="relative w-full max-w-2xl bg-gray-900 rounded-2xl overflow-hidden shadow-2xl border border-gray-800 animate-fade-in">

                {/* Backdrop header */}
                <div className="relative h-56 overflow-hidden">
                    {backdrop ? (
                        <img src={backdrop} alt={movie.title} className="w-full h-full object-cover" />
                    ) : (
                        <div className="w-full h-full bg-gradient-to-br from-gray-700 to-gray-900 flex items-center justify-center">
                            <Film size={48} className="text-gray-600" />
                        </div>
                    )}
                    {/* Gradient overlay */}
                    <div className="absolute inset-0 bg-gradient-to-t from-gray-900 via-gray-900/60 to-transparent" />

                    {/* Close button */}
                    <button
                        onClick={onClose}
                        className="absolute top-3 right-3 w-8 h-8 bg-black/60 hover:bg-black text-white rounded-full flex items-center justify-center transition-colors"
                    >
                        <X size={16} />
                    </button>

                    {/* Status badge */}
                    <div className="absolute top-3 left-3">
            <span className={`text-xs font-bold px-2.5 py-1 rounded-full ${status.cls}`}>
              {status.label}
            </span>
                    </div>
                </div>

                {/* Content */}
                <div className="flex gap-5 px-6 pb-6 -mt-16 relative">
                    {/* Poster */}
                    <div className="shrink-0">
                        {poster ? (
                            <img
                                src={poster}
                                alt={movie.title}
                                className="w-28 rounded-xl shadow-2xl border border-gray-700"
                            />
                        ) : (
                            <div className="w-28 h-40 bg-gray-800 rounded-xl border border-gray-700 flex items-center justify-center">
                                <Film size={28} className="text-gray-600" />
                            </div>
                        )}
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0 pt-16">
                        <h2 className="text-xl font-black text-white leading-tight mb-2">{movie.title}</h2>

                        {/* Meta row */}
                        <div className="flex flex-wrap items-center gap-3 mb-3">
                            {movie.rating != null && (
                                <span className="flex items-center gap-1 text-yellow-400 text-sm font-semibold">
                  <Star size={14} fill="currentColor" /> {movie.rating}
                </span>
                            )}
                            {movie.duration && (
                                <span className="flex items-center gap-1 text-gray-400 text-sm">
                  <Clock size={13} /> {movie.duration} min
                </span>
                            )}
                            {movie.language && (
                                <span className="flex items-center gap-1 text-gray-400 text-sm">
                  <Globe size={13} /> {movie.language}
                </span>
                            )}
                        </div>

                        {/* Genres */}
                        {movie.genre && (
                            <div className="flex flex-wrap gap-1.5 mb-4">
                                {movie.genre.split(',').map((g) => (
                                    <span key={g} className="text-xs bg-gray-800 text-gray-300 px-2.5 py-1 rounded-full border border-gray-700">
                    {g.trim()}
                  </span>
                                ))}
                            </div>
                        )}

                        {/* TMDB Overview */}
                        {tmdb?.overview && (
                            <p className="text-gray-400 text-sm leading-relaxed mb-4 line-clamp-4">
                                {tmdb.overview}
                            </p>
                        )}

                        {/* Extra TMDB info */}
                        {tmdb && (
                            <div className="grid grid-cols-2 gap-2 mb-5 text-xs">
                                {tmdb.release_date && (
                                    <div className="bg-gray-800 rounded-lg p-2.5">
                                        <p className="text-gray-500 mb-0.5">Release Date</p>
                                        <p className="text-gray-200 font-medium">{tmdb.release_date}</p>
                                    </div>
                                )}
                                {tmdb.vote_average > 0 && (
                                    <div className="bg-gray-800 rounded-lg p-2.5">
                                        <p className="text-gray-500 mb-0.5">TMDB Rating</p>
                                        <p className="text-gray-200 font-medium">⭐ {tmdb.vote_average.toFixed(1)} / 10</p>
                                    </div>
                                )}
                                {tmdb.original_language && (
                                    <div className="bg-gray-800 rounded-lg p-2.5">
                                        <p className="text-gray-500 mb-0.5">Original Language</p>
                                        <p className="text-gray-200 font-medium uppercase">{tmdb.original_language}</p>
                                    </div>
                                )}
                                {tmdb.popularity && (
                                    <div className="bg-gray-800 rounded-lg p-2.5">
                                        <p className="text-gray-500 mb-0.5">Popularity</p>
                                        <p className="text-gray-200 font-medium">{Math.round(tmdb.popularity)}</p>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Book button */}
                        {movie.status === 'NOW_SHOWING' && (
                            <button
                                onClick={() => { onClose(); navigate(isAuthenticated ? '/book' : '/login') }}
                                className="flex items-center gap-2 bg-red-600 hover:bg-red-700 text-white font-bold px-6 py-2.5 rounded-lg text-sm transition-all hover:scale-105 shadow-lg shadow-red-900/30"
                            >
                                <Ticket size={16} />
                                {isAuthenticated ? 'Book Now' : 'Sign In to Book'}
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    )
}
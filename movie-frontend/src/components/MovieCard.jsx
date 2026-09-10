import { useEffect, useState } from 'react'
import { searchMovie, posterUrl } from '../api/useTMDB'
import { Star, Clock, Ticket, Info, Film } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import MovieModal from './MovieModal'

export default function MovieCard({ movie }) {
    const [tmdb, setTmdb] = useState(null)
    const [showModal, setShowModal] = useState(false)
    const navigate = useNavigate()
    const { isAuthenticated } = useAuth()

    useEffect(() => {
        searchMovie(movie.title).then(setTmdb)
    }, [movie.title])

    const poster = posterUrl(tmdb?.poster_path, 'w342')

    const STATUS = {
        NOW_SHOWING: { label: 'Now Showing', cls: 'bg-red-600' },
        COMING_SOON: { label: 'Coming Soon', cls: 'bg-yellow-500 text-black' },
        ENDED: { label: 'Ended', cls: 'bg-gray-600' },
    }
    const status = STATUS[movie.status] ?? { label: movie.status, cls: 'bg-gray-600' }

    return (
        <>
            <div className="relative shrink-0 w-36 sm:w-40 md:w-44 cursor-pointer group">
                {/* Poster */}
                <div className="relative w-full aspect-[2/3] rounded-lg overflow-hidden bg-gray-800">
                    {poster ? (
                        <img
                            src={poster}
                            alt={movie.title}
                            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                        />
                    ) : (
                        <div className="w-full h-full flex flex-col items-center justify-center bg-gradient-to-br from-gray-700 to-gray-900 gap-2">
                            <Film size={28} className="text-gray-500" />
                            <p className="text-gray-500 text-xs px-2 text-center">{movie.title}</p>
                        </div>
                    )}

                    {/* Status badge */}
                    <div className="absolute top-2 left-2">
            <span className={`text-xs font-bold px-2 py-0.5 rounded-full text-white ${status.cls}`}>
              {status.label}
            </span>
                    </div>

                    {/* Hover overlay */}
                    <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                        {movie.status === 'NOW_SHOWING' && (
                            <button
                                onClick={() => navigate(isAuthenticated ? '/book' : '/login')}
                                className="flex items-center gap-1.5 bg-red-600 hover:bg-red-700 text-white text-xs font-bold px-4 py-2 rounded-full transition-colors"
                            >
                                <Ticket size={13} />
                                {isAuthenticated ? 'Book Now' : 'Sign In'}
                            </button>
                        )}
                        <button
                            onClick={() => setShowModal(true)}
                            className="flex items-center gap-1.5 bg-white/20 hover:bg-white/30 text-white text-xs font-medium px-4 py-2 rounded-full transition-colors"
                        >
                            <Info size={13} />
                            Details
                        </button>
                    </div>
                </div>

                {/* Info below */}
                <div className="mt-2 px-0.5">
                    <p className="text-white text-xs font-semibold leading-tight line-clamp-1">{movie.title}</p>
                    <div className="flex items-center justify-between mt-1">
                        {movie.rating != null && (
                            <span className="flex items-center gap-0.5 text-yellow-400 text-xs">
                <Star size={10} fill="currentColor" /> {movie.rating}
              </span>
                        )}
                        {movie.duration && (
                            <span className="flex items-center gap-0.5 text-gray-400 text-xs">
                <Clock size={10} /> {movie.duration}m
              </span>
                        )}
                    </div>
                </div>
            </div>

            {/* Movie Detail Modal */}
            {showModal && (
                <MovieModal movie={movie} onClose={() => setShowModal(false)} />
            )}
        </>
    )
}
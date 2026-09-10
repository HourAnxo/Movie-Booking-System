import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { movies as moviesApi } from '../api/endpoints'
import { searchMovie, backdropUrl, posterUrl } from '../api/useTMDB'
import { ErrorBox, Spinner } from '../components/Common'
import { useAuth } from '../auth/useAuth'
import { Ticket, ChevronLeft, ChevronRight, Star, Clock, Film, Info } from 'lucide-react'
import MovieCard from '../components/MovieCard'

export default function Movies() {
    const [list, setList] = useState(null)
    const [error, setError] = useState(null)
    const [hero, setHero] = useState(null)
    const [heroMovie, setHeroMovie] = useState(null)
    const { isAuthenticated } = useAuth()
    const navigate = useNavigate()

    const load = useCallback(() => {
        moviesApi
            .list()
            .then(async (data) => {
                setList(data)
                setError(null)
                const featured = data.find((m) => m.status === 'NOW_SHOWING') ?? data[0]
                if (featured) {
                    setHeroMovie(featured)
                    const tmdb = await searchMovie(featured.title)
                    setHero(tmdb)
                }
            })
            .catch(setError)
    }, [])

    useEffect(() => { load() }, [load])

    if (error) return (
        <div className="min-h-screen bg-[#141414] flex items-center justify-center p-8">
            <ErrorBox error={error} onRetry={load} />
        </div>
    )
    if (!list) return (
        <div className="min-h-screen bg-[#141414] flex items-center justify-center">
            <Spinner />
        </div>
    )

    const nowShowing = list.filter((m) => m.status === 'NOW_SHOWING')
    const comingSoon = list.filter((m) => m.status === 'COMING_SOON')
    const ended = list.filter((m) => m.status === 'ENDED')

    if (list.length === 0) {
        return (
            <div className="min-h-screen bg-[#141414] flex items-center justify-center flex-col gap-4">
                <Film size={48} className="text-gray-600" />
                <h2 className="text-xl font-semibold text-gray-400">No movies yet</h2>
                <p className="text-gray-600 text-sm">An admin can add movies from the Admin page.</p>
            </div>
        )
    }

    const backdrop = backdropUrl(hero?.backdrop_path, 'w1280')
    const heroPoster = posterUrl(hero?.poster_path, 'w342')

    return (
        <div className="min-h-screen bg-[#141414]">
            {/* Hero Banner */}
            {heroMovie && (
                <div className="relative w-full h-[75vh] min-h-[500px] overflow-hidden">
                    {backdrop ? (
                        <img src={backdrop} alt={heroMovie.title} className="absolute inset-0 w-full h-full object-cover" />
                    ) : (
                        <div className="absolute inset-0 bg-gradient-to-br from-gray-800 to-gray-900" />
                    )}
                    <div className="absolute inset-0 bg-gradient-to-r from-black/90 via-black/50 to-transparent" />
                    <div className="absolute inset-0 bg-gradient-to-t from-[#141414] via-transparent to-black/20" />

                    <div className="absolute inset-0 flex items-end pb-16 px-6 md:px-12 lg:px-16">
                        <div className="flex gap-8 items-end max-w-6xl w-full">
                            {heroPoster && (
                                <img
                                    src={heroPoster}
                                    alt={heroMovie.title}
                                    className="hidden sm:block w-36 md:w-44 rounded-xl shadow-2xl shrink-0 border border-white/10"
                                />
                            )}
                            <div className="flex-1">
                                <div className="flex items-center gap-2 mb-3">
                                    <span className="bg-red-600 text-white text-xs font-bold px-2.5 py-1 rounded-full">Now Showing</span>
                                    {heroMovie.rating != null && (
                                        <span className="flex items-center gap-1 text-yellow-400 text-sm font-semibold">
                      <Star size={14} fill="currentColor" /> {heroMovie.rating}
                    </span>
                                    )}
                                </div>
                                <h1 className="text-4xl md:text-5xl lg:text-6xl font-black text-white leading-tight mb-3 drop-shadow-lg">
                                    {heroMovie.title}
                                </h1>
                                <div className="flex flex-wrap gap-3 mb-4 text-sm text-gray-300">
                                    {heroMovie.genre && <span>{heroMovie.genre}</span>}
                                    {heroMovie.language && <span>· {heroMovie.language}</span>}
                                    {heroMovie.duration && (
                                        <span className="flex items-center gap-1">· <Clock size={13} /> {heroMovie.duration} min</span>
                                    )}
                                </div>
                                {hero?.overview && (
                                    <p className="text-gray-300 text-sm max-w-lg leading-relaxed mb-6 line-clamp-3">{hero.overview}</p>
                                )}
                                <div className="flex gap-3 flex-wrap">
                                    <button
                                        onClick={() => isAuthenticated ? navigate('/book') : navigate('/login')}
                                        className="flex items-center gap-2 bg-red-600 hover:bg-red-700 text-white font-bold px-7 py-3 rounded-lg transition-all hover:scale-105 shadow-lg shadow-red-900/40"
                                    >
                                        <Ticket size={18} />
                                        {isAuthenticated ? 'Book Now' : 'Sign In to Book'}
                                    </button>
                                    <button className="flex items-center gap-2 bg-white/20 hover:bg-white/30 backdrop-blur-sm text-white font-semibold px-7 py-3 rounded-lg transition-all hover:scale-105">
                                        <Info size={18} />
                                        More Info
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Movie Rows */}
            <div className="px-6 md:px-12 lg:px-16 pb-16 space-y-10 -mt-2 relative z-10">
                {nowShowing.length > 0 && <MovieRow title="🎬 Now Showing" movies={nowShowing} />}
                {comingSoon.length > 0 && <MovieRow title="🔜 Coming Soon" movies={comingSoon} />}
                {ended.length > 0 && <MovieRow title="📼 Recently Ended" movies={ended} />}
                {nowShowing.length === 0 && comingSoon.length === 0 && ended.length === 0 && (
                    <MovieRow title="All Movies" movies={list} />
                )}
            </div>
        </div>
    )
}

function MovieRow({ title, movies }) {
    const rowRef = useRef(null)
    const [canLeft, setCanLeft] = useState(false)
    const [canRight, setCanRight] = useState(true)

    function scroll(dir) {
        rowRef.current?.scrollBy({ left: dir * 600, behavior: 'smooth' })
    }

    function onScroll() {
        const el = rowRef.current
        if (!el) return
        setCanLeft(el.scrollLeft > 0)
        setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 10)
    }

    return (
        <div className="relative group/row">
            <h2 className="text-white font-bold text-lg mb-4">{title}</h2>
            {canLeft && (
                <button
                    onClick={() => scroll(-1)}
                    className="absolute left-0 top-1/2 -translate-y-1/2 z-20 w-10 h-10 bg-black/70 hover:bg-black text-white rounded-full flex items-center justify-center opacity-0 group-hover/row:opacity-100 transition-opacity shadow-lg mt-5"
                >
                    <ChevronLeft size={20} />
                </button>
            )}
            <div
                ref={rowRef}
                onScroll={onScroll}
                className="flex gap-3 overflow-x-auto pb-2"
                style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
            >
                {movies.map((m) => <MovieCard key={m.movieId} movie={m} />)}
            </div>
            {canRight && movies.length > 4 && (
                <button
                    onClick={() => scroll(1)}
                    className="absolute right-0 top-1/2 -translate-y-1/2 z-20 w-10 h-10 bg-black/70 hover:bg-black text-white rounded-full flex items-center justify-center opacity-0 group-hover/row:opacity-100 transition-opacity shadow-lg mt-5"
                >
                    <ChevronRight size={20} />
                </button>
            )}
        </div>
    )
}
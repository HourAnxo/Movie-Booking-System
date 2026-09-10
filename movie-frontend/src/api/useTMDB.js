/**
 * Fetches movie data from TMDB by title.
 * Uses the TMDB search endpoint and picks the first result.
 * The token is a Bearer read-access token (not the v3 API key).
 */

const TOKEN = import.meta.env.VITE_TMDB_TOKEN
const BASE = 'https://api.themoviedb.org/3'
const IMG = 'https://image.tmdb.org/t/p'

// In-memory cache so we don't hit TMDB on every render
const cache = new Map()

async function searchMovie(title) {
    if (cache.has(title)) return cache.get(title)

    if (!TOKEN) return null

    try {
        const res = await fetch(
            `${BASE}/search/movie?query=${encodeURIComponent(title)}&language=en-US&page=1`,
            {
                headers: {
                    Authorization: `Bearer ${TOKEN}`,
                    'Content-Type': 'application/json',
                },
            },
        )
        if (!res.ok) return null
        const data = await res.json()
        const movie = data.results?.[0] ?? null
        cache.set(title, movie)
        return movie
    } catch {
        return null
    }
}

// Poster sizes: w92 w154 w185 w342 w500 w780 original
export function posterUrl(path, size = 'w342') {
    if (!path) return null
    return `${IMG}/${size}${path}`
}

// Backdrop sizes: w300 w780 w1280 original
export function backdropUrl(path, size = 'w1280') {
    if (!path) return null
    return `${IMG}/${size}${path}`
}

export { searchMovie }
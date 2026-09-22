import { get, post, put, del } from './client'

export const auth = {
    register: (body) => post('/api/auth/register', body),
    login: (body) => post('/api/auth/login', body),
    profile: () => get('/api/auth/profile'),
    logout: (refreshToken) => post('/api/auth/logout', { refreshToken }),
    setRole: (username, role) => put(`/api/auth/users/${username}/role`, { role }),
}

export const movies = {
    list: () => get('/api/movies'),
    byId: (id) => get(`/api/movies/${id}`),
    create: (body) => post('/api/movies', body),
    remove: (id) => del(`/api/movies/${id}`),
}

export const theaters = {
    list: () => get('/api/theaters'),
    create: (body) => post('/api/theaters', body),
}

export const screens = {
    list: () => get('/api/screens'),
    create: (body) => post('/api/screens', body),
}

export const showtimes = {
    list: () => get('/api/showtimes'),
    create: (body) => post('/api/showtimes', body),
}

export const seats = {
    list: () => get('/api/seats'),
    byScreen: (screenId) => get(`/api/seats/screen/${screenId}`),
    create: (body) => post('/api/seats', body),
}

export const bookings = {
    list: () => get('/api/bookings'),
    byUser: (userId) => get(`/api/bookings/user/${userId}`),
    create: (body) => post('/api/bookings', body),
    cancel: (id) => put(`/api/bookings/${id}/cancel`),
}

export const payments = {
    // Issues a real KHQR. The amount is the booking's own; it is not sent.
    createBakong: (bookingId) => post('/api/payments/bakong', { bookingId }),
    // Asks payment-service to check with Bakong; poll until status is final.
    checkBakong: (id) => get(`/api/payments/${id}/bakong/check`),
    cancel: (id) => put(`/api/payments/${id}/cancel`),
}

export const admin = {
    users: () => get('/api/admin/users'),
}

export const health = {
    services: [
        { name: 'API Gateway',      key: 'api-gateway',      url: 'http://localhost:8083/actuator/health', icon: '🌐' },
        { name: 'Auth Service',     key: 'auth-service',      url: 'http://localhost:8083/api/auth/health', icon: '🔐', fallback: true },
        { name: 'User Service',     key: 'user-service',      url: 'http://localhost:8083/api/users/health', icon: '👤', fallback: true },
        { name: 'Movie Service',    key: 'movie-service',     url: 'http://localhost:8083/api/movies/actuator/health', icon: '🎬', fallback: true },
        { name: 'Theater Service',  key: 'theater-service',   url: 'http://localhost:8083/api/theaters/actuator/health', icon: '🏛️', fallback: true },
        { name: 'Screen Service',   key: 'screen-service',    url: 'http://localhost:8083/api/screens/actuator/health', icon: '📽️', fallback: true },
        { name: 'Showtime Service', key: 'showtime-service',  url: 'http://localhost:8083/api/showtimes/actuator/health', icon: '🕐', fallback: true },
        { name: 'Seat Service',     key: 'seat-service',      url: 'http://localhost:8083/api/seats/actuator/health', icon: '💺', fallback: true },
        { name: 'Booking Service',  key: 'booking-service',   url: 'http://localhost:8083/api/bookings/actuator/health', icon: '🎟️', fallback: true },
        { name: 'Payment Service',  key: 'payment-service',   url: 'http://localhost:8083/api/payments/actuator/health', icon: '💳', fallback: true },
        { name: 'Admin Service',    key: 'admin-service',     url: 'http://localhost:8083/api/admin/actuator/health', icon: '⚙️', fallback: true },
        { name: 'Discovery Server', key: 'discovery-server',  url: 'http://localhost:8761/actuator/health', icon: '🔍' },
    ],

    check: async (url) => {
        try {
            const res = await fetch(url, { signal: AbortSignal.timeout(4000) })
            if (!res.ok) return 'DOWN'
            const data = await res.json()
            return data?.status === 'UP' ? 'UP' : 'DOWN'
        } catch {
            return 'DOWN'
        }
    },
}
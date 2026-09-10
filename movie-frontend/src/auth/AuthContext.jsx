import { useMemo, useState } from 'react'
import { AuthContext } from './context'
import { tokens } from '../api/client'
import { auth as authApi } from '../api/endpoints'

export function AuthProvider({ children }) {
  // Seeded from localStorage so a page reload does not log the user out.
  const [user, setUser] = useState(() => tokens.user())

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: Boolean(user),
      isAdmin: user?.role === 'ADMIN',

      async login(username, password) {
        const data = await authApi.login({ username, password })
        tokens.save(data)
        setUser({ username: data.username, userId: data.userId, role: data.role })
        return data
      },

      async register(form) {
        const data = await authApi.register(form)
        tokens.save(data)
        setUser({ username: data.username, userId: data.userId, role: data.role })
        return data
      },

      logout() {
        // Fire-and-forget: the server-side call revokes nothing, so there is
        // nothing to wait for and nothing to fail.
        authApi.logout(tokens.refresh()).catch(() => {})
        tokens.clear()
        setUser(null)
      },
    }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

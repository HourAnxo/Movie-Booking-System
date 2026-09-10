import { createContext } from 'react'

/**
 * Split from the provider so that AuthContext.jsx exports only a component.
 * Vite's Fast Refresh gives up on a module that mixes components with other
 * exports, which means losing state on every save while developing.
 */
export const AuthContext = createContext(null)

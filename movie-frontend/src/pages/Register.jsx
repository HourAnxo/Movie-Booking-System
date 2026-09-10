import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ErrorBox } from '../components/Common'
import { Film, Eye, EyeOff } from 'lucide-react'
import toast from 'react-hot-toast'

export default function Register() {
    const { register } = useAuth()
    const navigate = useNavigate()
    const [form, setForm] = useState({ username: '', email: '', password: '', name: '', phone: '' })
    const [error, setError] = useState(null)
    const [busy, setBusy] = useState(false)
    const [showPass, setShowPass] = useState(false)
    const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

    async function submit(e) {
        e.preventDefault()
        setBusy(true); setError(null)
        try {
            await register(form)
            toast.success('Welcome to CineBook!')
            navigate('/', { replace: true })
        } catch (err) { setError(err) }
        finally { setBusy(false) }
    }

    const inp = 'w-full px-3 py-2.5 bg-gray-800 border border-gray-700 rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-transparent transition'

    return (
        <div className="min-h-[80vh] flex items-center justify-center py-8">
            <div className="w-full max-w-sm">
                <div className="text-center mb-8">
                    <div className="inline-flex items-center justify-center w-14 h-14 bg-red-600 rounded-2xl mb-3 shadow-lg shadow-red-900/50">
                        <Film size={26} className="text-white" />
                    </div>
                    <h1 className="text-2xl font-bold text-white">Create account</h1>
                    <p className="text-gray-500 text-sm mt-1">Join CineBook and start booking</p>
                </div>

                <div className="bg-gray-900 rounded-2xl border border-gray-800 p-6">
                    <form onSubmit={submit} className="space-y-4">
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-1.5">Username</label>
                                <input name="username" value={form.username} onChange={change} required placeholder="username" className={inp} />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-1.5">Full name</label>
                                <input name="name" value={form.name} onChange={change} required placeholder="Your name" className={inp} />
                            </div>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-300 mb-1.5">Email</label>
                            <input name="email" type="email" value={form.email} onChange={change} required placeholder="you@example.com" className={inp} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-300 mb-1.5">Password</label>
                            <div className="relative">
                                <input name="password" type={showPass ? 'text' : 'password'} minLength={8} value={form.password} onChange={change} required placeholder="Min. 8 characters" className={`${inp} pr-10`} />
                                <button type="button" onClick={() => setShowPass(!showPass)} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300">
                                    {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                                </button>
                            </div>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-300 mb-1.5">
                                Phone <span className="text-gray-600 font-normal">(optional)</span>
                            </label>
                            <input name="phone" value={form.phone} onChange={change} placeholder="012 345 678" maxLength={20} className={inp} />
                        </div>
                        <ErrorBox error={error} />
                        <button type="submit" disabled={busy}
                                className="w-full bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white font-bold py-2.5 rounded-lg text-sm transition-colors shadow-lg shadow-red-900/30">
                            {busy ? 'Creating account...' : 'Create Account'}
                        </button>
                    </form>
                </div>
                <p className="text-center text-sm text-gray-500 mt-4">
                    Already have an account?{' '}
                    <Link to="/login" className="text-red-500 font-medium hover:text-red-400">Sign in</Link>
                </p>
            </div>
        </div>
    )
}

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login, getUserProfile } from '../services/authService';
import useUser from '../hooks/useUser';

export default function LoginPage() {
  const navigate = useNavigate();
  const { setUser } = useUser();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [notice] = useState(() => sessionStorage.getItem('palacetec:notice'));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login(email, password);
      sessionStorage.removeItem('palacetec:notice');

      const userData = await getUserProfile();

      setUser({
        id: userData.id,
        email: userData.email,
        name: userData.nickname || userData.email.split('@')[0],
        nickname: userData.nickname || userData.email.split('@')[0],
        avatar_url: userData.avatar_url || null,
      });

      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="main-sections">
      <section className="bet-controls">
        <h1>Iniciar sesión</h1>
        {notice && <p role="status">{notice}</p>}
        <p>Ingresa a tu cuenta de PalaceTec.</p>

        <form onSubmit={handleSubmit}>
          <label htmlFor="login-email">Correo electrónico</label>
          <input
            id="login-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />

          <label htmlFor="login-password">Contraseña</label>
          <input
            id="login-password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />

          {error && <p role="alert">{error}</p>}

          <button
            type="submit"
            className="place-bet-button"
            disabled={loading}
          >
            {loading ? 'Ingresando...' : 'Iniciar sesión'}
          </button>
        </form>
      </section>
    </main>
  );
}

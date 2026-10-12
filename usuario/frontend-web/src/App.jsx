import Header from './components/Header';
import PasswordStrength from './components/PasswordStrength';
import './App.css';

const demoUser = {
  id: 1,
  name: 'Jugador de Prueba',
  nickname: 'JugadorDemo',
  email: 'jugador@palacetec.test',
};

function App() {
  return (
    <div className="app-layout">
      <Header user={demoUser} />

      <main className="app-content">
        <PasswordStrength />
      </main>
    </div>
  );
}

export default App;

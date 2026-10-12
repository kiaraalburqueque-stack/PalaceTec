import Header from './components/Header';
import PasswordStrength from './components/PasswordStrength';
import UserProvider from './context/UserProvider';
import './App.css';

function App() {
  return (
    <UserProvider>
      <div className="app-layout">
        <Header />

        <main className="app-content">
          <PasswordStrength />
        </main>
      </div>
    </UserProvider>
  );
}

export default App;

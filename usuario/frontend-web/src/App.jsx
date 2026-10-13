import Header from './components/Header';
import PasswordStrength from './components/PasswordStrength';
import PreBetBalance from './components/PreBetBalance';
import UserProvider from './context/UserProvider';
import './App.css';

function App() {
  return (
    <UserProvider>
      <div className="app-layout">
        <Header />

        <main className="app-content">
          <div className="main-sections">
            <PreBetBalance balance={1000} />
            <PasswordStrength />
          </div>
        </main>
      </div>
    </UserProvider>
  );
}

export default App;

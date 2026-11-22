import { useEffect, useState } from 'react';

// HU62: devuelve el valor solo cuando deja de cambiar durante `delay` ms (350 ms por defecto).
export default function useDebounce(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

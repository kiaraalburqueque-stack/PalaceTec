import { it, expect, vi, afterEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import useDebounce from './useDebounce';

afterEach(() => vi.useRealTimers());

it('HU62 useDebounce entrega el valor solo después de 350 ms sin cambios', () => {
  vi.useFakeTimers();
  const { result, rerender } = renderHook(({ value }) => useDebounce(value, 350), { initialProps: { value: '' } });
  rerender({ value: 'ru' });
  act(() => vi.advanceTimersByTime(200));
  rerender({ value: 'rule' });
  act(() => vi.advanceTimersByTime(349));
  expect(result.current).toBe('');
  act(() => vi.advanceTimersByTime(1));
  expect(result.current).toBe('rule');
});

it('HU62 useDebounce usa 350 ms por defecto', () => {
  vi.useFakeTimers();
  const { result, rerender } = renderHook(({ value }) => useDebounce(value), { initialProps: { value: 'a' } });
  rerender({ value: 'b' });
  act(() => vi.advanceTimersByTime(349));
  expect(result.current).toBe('a');
  act(() => vi.advanceTimersByTime(1));
  expect(result.current).toBe('b');
});

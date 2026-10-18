export const WHEEL_NUMBERS = [0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26];
export function winningRotation(number, from = 0) {
  return (Math.ceil(from / 360) + 5) * 360 - WHEEL_NUMBERS.indexOf(number) * (360 / 37);
}

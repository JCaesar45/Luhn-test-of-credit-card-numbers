function luhnTest(str) {
  // Reverse the digits
  const digits = str.split('').reverse().map(Number);
  
  let s1 = 0;
  let s2 = 0;
  
  for (let i = 0; i < digits.length; i++) {
    if (i % 2 === 0) {
      // Odd positions in reversed (1st, 3rd, ...) → index 0, 2, 4...
      s1 += digits[i];
    } else {
      // Even positions in reversed (2nd, 4th, ...) → index 1, 3, 5...
      let doubled = digits[i] * 2;
      // Sum the digits if result > 9
      if (doubled > 9) {
        doubled = Math.floor(doubled / 10) + (doubled % 10);
      }
      s2 += doubled;
    }
  }
  
  return (s1 + s2) % 10 === 0;
}

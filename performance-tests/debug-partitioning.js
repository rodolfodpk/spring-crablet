import {getWalletId, getWalletRangeForVU} from './config.js';

// Debug partitioning logic
console.log('Testing wallet partitioning logic:');
console.log('Total VUs: 10');
console.log('Wallet pool size: 100');

for (let vuId = 1; vuId <= 10; vuId++) {
    const range = getWalletRangeForVU(vuId, 10);
    console.log(`VU ${vuId}: wallets ${range.startIndex}-${range.endIndex} (${getWalletId(range.startIndex)} to ${getWalletId(range.endIndex)})`);
}

// Test wallet pair generation for VU 1
console.log('\nTesting wallet pair generation for VU 1:');
const range1 = getWalletRangeForVU(1, 10);
console.log(`VU 1 range: ${range1.startIndex}-${range1.endIndex}`);

// Generate a few pairs
for (let i = 0; i < 5; i++) {
    const wallet1Index = Math.floor(Math.random() * (range1.endIndex - range1.startIndex + 1)) + range1.startIndex;
    let wallet2Index;
    do {
        wallet2Index = Math.floor(Math.random() * (range1.endIndex - range1.startIndex + 1)) + range1.startIndex;
    } while (wallet2Index === wallet1Index);

    console.log(`Pair ${i + 1}: ${getWalletId(wallet1Index)} -> ${getWalletId(wallet2Index)}`);
}

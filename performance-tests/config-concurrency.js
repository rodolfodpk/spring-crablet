export const config = {
    BASE_URL: 'http://localhost:8080',
    ENDPOINTS: {
        WALLET: '/api/wallets',
        TRANSFER: '/api/wallets/transfer',
        DEPOSIT: '/api/wallets/{walletId}/deposit',
        HISTORY: '/api/wallets/{walletId}/history',
        HEALTH: '/actuator/health'
    },
    WALLET_POOL_SIZE: 3,  // Only 3 wallets!
    WALLET_PREFIX: 'concurrency-wallet-',
    INITIAL_BALANCE_MIN: 50000,
    INITIAL_BALANCE_MAX: 100000,
    TRANSFER_AMOUNT_MIN: 50,
    TRANSFER_AMOUNT_MAX: 200
};

// Helper to generate wallet IDs
export function getWalletId(index) {
    return `${config.WALLET_PREFIX}${String(index).padStart(3, '0')}`;
}

// Helper to get random balance within range
export function getRandomBalance() {
    return Math.floor(Math.random() * (config.INITIAL_BALANCE_MAX - config.INITIAL_BALANCE_MIN + 1)) + config.INITIAL_BALANCE_MIN;
}

// Helper to get random transfer amount
export function getRandomTransferAmount() {
    return Math.floor(Math.random() * (config.TRANSFER_AMOUNT_MAX - config.TRANSFER_AMOUNT_MIN + 1)) + config.TRANSFER_AMOUNT_MIN;
}

// Helper to get wallet range for a VU (for partitioning)
export function getWalletRangeForVU(vuId, totalVUs) {
    const walletsPerVU = Math.floor(config.WALLET_POOL_SIZE / totalVUs);
    const startIndex = (vuId - 1) * walletsPerVU + 1;
    const endIndex = Math.min(vuId * walletsPerVU, config.WALLET_POOL_SIZE);
    return {startIndex, endIndex};
}

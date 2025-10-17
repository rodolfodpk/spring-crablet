// Centralized configuration for performance tests
export const config = {
  // Base URL for the API
  BASE_URL: __ENV.BASE_URL || 'http://localhost:8080',
  
  // Wallet pool configuration
  WALLET_POOL_SIZE: parseInt(__ENV.WALLET_POOL_SIZE) || 100,
  WALLET_PREFIX: __ENV.WALLET_PREFIX || 'perf-wallet-',
  INITIAL_BALANCE_MIN: parseInt(__ENV.INITIAL_BALANCE_MIN) || 500,
  INITIAL_BALANCE_MAX: parseInt(__ENV.INITIAL_BALANCE_MAX) || 10000,
  
  // Test behavior
  CLEANUP_AFTER_TEST: __ENV.CLEANUP_AFTER_TEST !== 'false',
  PARTITION_WALLETS_BY_VU: __ENV.PARTITION_WALLETS_BY_VU !== 'false',
  
  // API endpoints
  ENDPOINTS: {
    WALLET: '/api/wallets',
    TRANSFER: '/api/wallets/transfer',
    HEALTH: '/actuator/health'
  }
};

// Helper to get wallet ID by index
export function getWalletId(index) {
  return `${config.WALLET_PREFIX}${String(index).padStart(3, '0')}`;
}

// Helper to get random balance within range
export function getRandomBalance() {
  return Math.floor(Math.random() * (config.INITIAL_BALANCE_MAX - config.INITIAL_BALANCE_MIN + 1)) + config.INITIAL_BALANCE_MIN;
}

// Helper to get wallet range for a VU (for partitioning)
export function getWalletRangeForVU(vuId, totalVUs) {
  const walletsPerVU = Math.floor(config.WALLET_POOL_SIZE / totalVUs);
  const startIndex = (vuId - 1) * walletsPerVU + 1;
  const endIndex = Math.min(vuId * walletsPerVU, config.WALLET_POOL_SIZE);
  return { startIndex, endIndex };
}

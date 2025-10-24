// Generate verified wallet pool (1000 wallets)
let walletData = {
    wallets: [],
    count: 1000,
    timestamp: Date.now()
};

// Generate wallet IDs from success-wallet-001 to success-wallet-1000
for (let i = 1; i <= 1000; i++) {
    walletData.wallets.push(`success-wallet-${String(i).padStart(3, '0')}`);
}

console.info(`📊 Generated wallet pool with ${walletData.count} verified wallets`);

let walletPool = [];

/**
 * Initialize the wallet pool with verified wallets from seeding
 */
export function initializeWalletPool() {
    try {
        // Use the hardcoded wallet data instead of trying to load from file
        walletPool = [...walletData.wallets];
        console.info(`📊 Initialized wallet pool with ${walletPool.length} verified wallets`);

        if (walletPool.length === 0) {
            throw new Error('No verified wallets available - wallet pool is empty');
        }

        if (walletPool.length < 10) {
            console.warn(`⚠️  Low wallet pool size: ${walletPool.length} wallets. Consider increasing pool size.`);
        }
    } catch (error) {
        throw new Error(`Failed to initialize wallet pool: ${error.message}`);
    }
}

/**
 * Get a random wallet from the verified pool
 */
export function getRandomWallet() {
    if (walletPool.length === 0) {
        throw new Error('Wallet pool not initialized or empty. Call initializeWalletPool() first.');
    }
    const randomIndex = Math.floor(Math.random() * walletPool.length);
    return walletPool[randomIndex];
}

/**
 * Get two different wallets from the verified pool for transfer operations
 */
export function getWalletPair() {
    if (walletPool.length < 2) {
        throw new Error(`Insufficient verified wallets for transfer. Pool size: ${walletPool.length}, required: 2`);
    }

    const wallet1Index = Math.floor(Math.random() * walletPool.length);
    let wallet2Index;
    do {
        wallet2Index = Math.floor(Math.random() * walletPool.length);
    } while (wallet2Index === wallet1Index);

    return {
        fromWalletId: walletPool[wallet1Index],
        toWalletId: walletPool[wallet2Index]
    };
}

/**
 * Get the current pool size
 */
export function getPoolSize() {
    return walletPool.length;
}

/**
 * Check if wallet pool is initialized
 */
export function isInitialized() {
    return walletPool.length > 0;
}

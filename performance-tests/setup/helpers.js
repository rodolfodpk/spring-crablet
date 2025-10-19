import http from 'k6/http';

// Note: getRandomWallet and getWalletPair functions moved to wallet-pool.js
// These functions now use the verified wallet pool instead of random selection

/**
 * Get wallet IDs partitioned by VU to avoid concurrency conflicts
 */
export function getWalletPairForVU(config, vuId, totalVUs) {
    const {startIndex, endIndex} = getWalletRangeForVU(config, vuId, totalVUs);
    const wallet1Index = Math.floor(Math.random() * (endIndex - startIndex + 1)) + startIndex;
    let wallet2Index;
    do {
        wallet2Index = Math.floor(Math.random() * (endIndex - startIndex + 1)) + startIndex;
    } while (wallet2Index === wallet1Index);

    return {
        fromWalletId: getWalletId(config, wallet1Index),
        toWalletId: getWalletId(config, wallet2Index)
    };
}

// Helper functions for config
function getWalletId(config, index) {
    return `${config.WALLET_PREFIX}${String(index).padStart(3, '0')}`;
}

function getWalletRangeForVU(config, vuId, totalVUs) {
    const walletsPerVU = Math.floor(config.WALLET_POOL_SIZE / totalVUs);
    const startIndex = (vuId - 1) * walletsPerVU + 1;
    const endIndex = Math.min(vuId * walletsPerVU, config.WALLET_POOL_SIZE);
    return {startIndex, endIndex};
}

/**
 * Check if a wallet exists by making a GET request
 */
export function checkWalletExists(config, walletId) {
    const response = http.get(`${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`);
    return response.status === 200;
}

/**
 * Ensure a wallet has sufficient balance by depositing if needed
 */
export function ensureBalance(config, walletId, minAmount) {
    // First check current balance
    const response = http.get(`${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`);

    if (response.status !== 200) {
        console.warn(`Wallet ${walletId} does not exist`);
        return false;
    }

    try {
        const walletData = JSON.parse(response.body);
        const currentBalance = walletData.balance || 0;

        if (currentBalance >= minAmount) {
            return true; // Already has sufficient balance
        }

        // Deposit enough to reach minimum amount
        const depositAmount = minAmount - currentBalance + 1000; // Extra buffer
        const depositPayload = JSON.stringify({
            depositId: `ensure-balance-${Date.now()}-${Math.random()}`,
            amount: depositAmount,
            description: 'Balance replenishment for performance test'
        });

        const depositResponse = http.post(
            `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}/deposit`,
            depositPayload,
            {headers: {'Content-Type': 'application/json'}}
        );

        return depositResponse.status === 201 || depositResponse.status === 200;
    } catch (error) {
        console.error(`Error ensuring balance for wallet ${walletId}:`, error);
        return false;
    }
}

/**
 * Create a wallet with specified parameters
 */
export function createWallet(config, walletId, owner, initialBalance) {
    const payload = JSON.stringify({
        owner: owner,
        initialBalance: initialBalance
    });

    const response = http.put(
        `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`,
        payload,
        {headers: {'Content-Type': 'application/json'}}
    );

    return {
        success: response.status === 200 || response.status === 201,
        response: response
    };
}

/**
 * Perform a transfer between two wallets
 */
export function performTransfer(config, fromWalletId, toWalletId, amount, description = 'Performance test transfer') {
    // Use a more unique ID to avoid conflicts under load
    const transferId = `transfer-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const payload = JSON.stringify({
        transferId: transferId,
        fromWalletId: fromWalletId,
        toWalletId: toWalletId,
        amount: amount,
        description: description
    });

    const response = http.post(
        `${config.BASE_URL}${config.ENDPOINTS.TRANSFER}`,
        payload,
        {headers: {'Content-Type': 'application/json'}}
    );

    return {
        success: response.status === 201 || response.status === 200,
        response: response,
        transferId: transferId
    };
}

/**
 * Perform a deposit to a wallet
 */
export function performDeposit(config, walletId, amount, description = 'Performance test deposit') {
    // Use a more unique ID to avoid conflicts under load
    const depositId = `deposit-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const payload = JSON.stringify({
        depositId: depositId,
        amount: amount,
        description: description
    });

    const response = http.post(
        `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}/deposit`,
        payload,
        {headers: {'Content-Type': 'application/json'}}
    );

    return {
        success: response.status === 201 || response.status === 200,
        response: response,
        depositId: depositId
    };
}

/**
 * Get wallet balance
 */
export function getWalletBalance(config, walletId) {
    const response = http.get(`${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}`);

    if (response.status !== 200) {
        return null;
    }

    try {
        const walletData = JSON.parse(response.body);
        return walletData.balance || 0;
    } catch (error) {
        return null;
    }
}

/**
 * Perform a withdrawal from a wallet
 */
export function performWithdrawal(config, walletId, amount, description = 'Performance test withdrawal') {
    // Use a more unique ID to avoid conflicts under load
    const withdrawalId = `withdrawal-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const payload = JSON.stringify({
        withdrawalId: withdrawalId,
        amount: amount,
        description: description
    });

    const response = http.post(
        `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}/withdraw`,
        payload,
        {headers: {'Content-Type': 'application/json'}}
    );

    return {
        success: response.status === 201 || response.status === 200,
        response: response,
        withdrawalId: withdrawalId
    };
}

/**
 * Get wallet events with pagination
 */
export function getWalletEvents(config, walletId, page = 0, size = 10) {
    const response = http.get(
        `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}/events?page=${page}&size=${size}`
    );

    return {
        success: response.status === 200,
        response: response
    };
}

/**
 * Get wallet commands with pagination
 */
export function getWalletCommands(config, walletId, page = 0, size = 10) {
    const response = http.get(
        `${config.BASE_URL}${config.ENDPOINTS.WALLET}/${walletId}/commands?page=${page}&size=${size}`
    );

    return {
        success: response.status === 200,
        response: response
    };
}

/**
 * Check if the API is healthy
 */
export function checkApiHealth(config) {
    const response = http.get(`${config.BASE_URL}${config.ENDPOINTS.HEALTH}`);
    return response.status === 200;
}

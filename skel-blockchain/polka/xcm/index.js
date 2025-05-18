import { ApiPromise, WsProvider } from '@polkadot/api';
import { config } from 'dotenv';
import { hexToString } from '@polkadot/util';

config();

const NETWORKS = {
    polkadot: {
        name: 'Polkadot',
        endpoint: 'wss://rpc.polkadot.io'
    },
    bifrost: {
        name: 'Bifrost',
        endpoint: 'wss://bifrost-rpc.liebi.com/ws'
    },
    hydration: {
        name: 'Hydration',
        endpoint: 'wss://rpc.hydration.dev'
    },
    asset_hub: {
        name: 'Asset Hub',
        endpoint: 'wss://polkadot-asset-hub-rpc.polkadot.io'
    },
    moonbeam: {
        name: 'Moonbeam',
        endpoint: 'wss://wss.api.moonbeam.network'
    }
};

const selectedNetwork = (process.env.NETWORK || 'polkadot').toLowerCase();
const POLKADOT_NODE_WS = NETWORKS[selectedNetwork]?.endpoint;
const args = process.argv.slice(2);
const targetBlockId = args[0];

if (!POLKADOT_NODE_WS) {
    console.error(`❌ Invalid network: ${process.env.NETWORK}`);
    console.log('Available networks:', Object.keys(NETWORKS).join(', '));
    process.exit(1);
}

const XCM_SECTIONS = {
    DMP: 'dmp',
    UMP: 'ump',
    HRMP: 'hrmp',
    XCM_PALLET: 'xcmPallet',
    POLKADOT_XCM: 'polkadotXcm',
    XCM: 'xcm',
    XCMP_QUEUE: 'xcmpQueue',
    SLPX: 'slpx',
    EMA_ORACLE: 'emaOracle',
    MESSAGING_STATE: 'messagingState'
};

async function connectToPolkadot() {
    console.log('Creating WS provider...');
    const provider = new WsProvider(POLKADOT_NODE_WS);
    
    console.log('Creating API...');
    const api = await ApiPromise.create({ 
        provider,
        noInitWarn: true
    });

    console.log('Waiting for API ready...');
    await api.isReady;
    
    console.log('API is ready');
    return api;
}

async function getBlockApi(api, blockHash) {
    // Get the runtime version at this block
    const runtimeVersion = await api.rpc.state.getRuntimeVersion(blockHash);
    
    // Get the metadata at this block
    const metadata = await api.rpc.state.getMetadata(blockHash);
    
    // Create a new API instance with the historical types
    const apiAt = await api.at(blockHash, metadata);
    
    return apiAt;
}

async function processBlockData(api, blockHash, blockNumber) {
    try {
        console.log('Fetching block...');
        const block = await api.rpc.chain.getBlock(blockHash)
            .catch(e => {
                console.error('Failed to get block:', e.message);
                throw e;
            });

        console.log('Fetching events...');
        const events = await api.query.system.events.at(blockHash)
            .catch(e => {
                console.error('Failed to get events:', e.message);
                throw e;
            });

        console.log('Fetching timestamp...');
        const timestamp = await api.query.timestamp.now.at(blockHash)
            .catch(e => {
                console.error('Failed to get timestamp:', e.message);
                throw e;
            });
        
        console.log('Processing block data...');
        console.log(`\n🔗 Block #${blockNumber}`);
        console.log(`📍 Hash: ${blockHash}`);
        console.log(`⏰ ${new Date(timestamp.toNumber()).toISOString()}`);
        console.log(`📦 ${block.block.extrinsics.length} extrinsics, ${events.length} events`);
        
        // Group events by section and method
        const eventGroups = {};
        let xcmEventCount = 0;

        console.log('Processing events...');
        for (const record of events) {
            try {
                const { event } = record;
                const { section, method } = event;
                
                const key = `${section}.${method}`;
                eventGroups[key] = (eventGroups[key] || 0) + 1;

                if (Object.values(XCM_SECTIONS).includes(section.toLowerCase())) {
                    xcmEventCount++;
                }
            } catch (eventError) {
                console.warn('⚠️ Failed to process an event:', eventError.message);
                continue;
            }
        }

        console.log('\n📋 Event Groups:');
        Object.entries(eventGroups)
            .sort(([a], [b]) => a.localeCompare(b))
            .forEach(([key, count]) => {
                const [section] = key.split('.');
                const isXcm = Object.values(XCM_SECTIONS).includes(section.toLowerCase());
                console.log(`${isXcm ? '🌟' : '▪️'} ${key}: ${count}`);
            });

        if (xcmEventCount > 0) {
            console.log(`\n📝 Total XCM events: ${xcmEventCount}`);
        }
    } catch (error) {
        console.error('Full error object:', error);
        console.error('Error stack:', error.stack);
        throw new Error(`Failed to process block data: ${error.message}`);
    }
}

async function processBlock(api, blockId) {
    try {
        let blockHash;
        let blockNumber;
        
        // Determine block hash and number
        if (isBlockHash(blockId)) {
            blockHash = blockId;
            console.log(`\n🔍 Processing block with hash: ${blockId}`);
            const header = await api.rpc.chain.getHeader(blockHash);
            blockNumber = header.number.toNumber();
        } else {
            blockNumber = parseInt(blockId);
            if (isNaN(blockNumber)) {
                throw new Error('Invalid block number or hash');
            }
            console.log(`\n🔍 Processing block number: ${blockNumber}`);
            blockHash = await api.rpc.chain.getBlockHash(blockNumber);
        }

        await processBlockData(api, blockHash, blockNumber);
    } catch (error) {
        console.error(`Error processing block:`, error.message);
        if (error.message.includes('Unable to find Call')) {
            console.error('\n⚠️ This error usually occurs when trying to decode very old blocks.');
            console.error('Try a more recent block or check if you\'re connected to the correct network.');
        }
    }
}

async function monitorBlocksAndTransfers(api) {
    console.log('🔍 Monitoring blocks and XCM transfers...\n');

    let blockCount = 0;
    let lastLogTime = Date.now();
    const startTime = Date.now();
    const LOG_INTERVAL = 60 * 1000;

    const unsubscribe = await api.rpc.chain.subscribeFinalizedHeads(async (header) => {
        try {
            blockCount++;
            const currentTime = Date.now();

            if (currentTime - lastLogTime >= LOG_INTERVAL) {
                const elapsedTime = ((currentTime - startTime) / 1000).toFixed(1);
                const blocksPerSecond = (blockCount / (currentTime - startTime) * 1000).toFixed(2);
                console.log(`\n📊 Stats: ${blocksPerSecond} blocks/s, ${blockCount} total blocks in ${elapsedTime}s`);
                lastLogTime = currentTime;
            }

            const blockHash = await api.rpc.chain.getBlockHash(header.number);
            await processBlockData(api, blockHash, header.number.toNumber());
        } catch (error) {
            console.error(`Error processing block ${header.number}:`, error.message);
        }
    });

    process.on('SIGINT', async () => {
        console.log('\nShutting down...');
        unsubscribe();
        await api.disconnect();
        process.exit(0);
    });
}

function isBlockHash(str) {
    return /^0x[0-9a-fA-F]{64}$/.test(str);
}

async function main() {
    try {
        console.log(`\n🌟 Starting ${NETWORKS[selectedNetwork].name} Monitor`);
        console.log(`📡 Connecting to ${POLKADOT_NODE_WS}\n`);
        
        const api = await connectToPolkadot();
        console.log('Connected successfully');

        // Log API details
        console.log('API version:', api.version);
        console.log('Runtime version:', api.runtimeVersion.toString());

        if (targetBlockId) {
            await processBlock(api, targetBlockId);
            await api.disconnect();
            process.exit(0);
        } else {
            await monitorBlocksAndTransfers(api);
        }
    } catch (error) {
        console.error('Fatal error:', error);
        console.error('Error stack:', error.stack);
        process.exit(1);
    }
}

main(); 
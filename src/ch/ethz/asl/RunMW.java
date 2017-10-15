package ch.ethz.asl;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;
import ch.ethz.asl.net.ClientsSocketsHandler;

public class RunMW {

	static int POOLTHREAD_KEEP_ALIVE_MS = 5000;
	static int THREADPOOL_AWAIT_TERMINATION_TIMEOUT_MS = 2000;
	static String myIp = null;
	static int myPort = 0;
	static List<String> mcAddresses = null;
	static int numThreadsPTP = -1;
	public static boolean readSharded = false;
	
	static ExecutorService threadPool = null;
	static ClientsSocketsHandler sockHandler = null;
	private static BlockingQueue<Runnable> channelQueue = null;
	private static final Logger logger = LogManager.getLogger(RunMW.class);
	
	
	/***
	 * The maximum number of readable channels in the queue.
	 * This is the de-facto maximum number of concurrent clients.
	 */
	static final int MAX_CHANNEL_QUEUE_CAPACITY = 4096;
	
	public static void main(String[] args) throws Exception {

		// -----------------------------------------------------------------------------
		// Add shutdown hook
		// -----------------------------------------------------------------------------
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
            public void run() {
				logger.info("Caught shutdown signal.");
                RunMW.shutdown();
            }   
		});
		
		// -----------------------------------------------------------------------------
		// Parse and prepare arguments
		// -----------------------------------------------------------------------------
		
		parseArguments(args);

		// -----------------------------------------------------------------------------
		// Start the Middleware
		// -----------------------------------------------------------------------------
		MemcachedSocketHandler.setMcAddresses(mcAddresses);
		channelQueue = new LinkedBlockingQueue<Runnable>();
		threadPool = new ThreadPoolExecutor(numThreadsPTP, numThreadsPTP, POOLTHREAD_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS, channelQueue);
		sockHandler = new ClientsSocketsHandler(myIp, myPort, threadPool);
		new Thread(sockHandler).start();

	}

	private static void parseArguments(String[] args) {
		Map<String, List<String>> params = new HashMap<>();

		List<String> options = null;
		for (int i = 0; i < args.length; i++) {
			final String a = args[i];

			if (a.charAt(0) == '-') {
				if (a.length() < 2) {
					logger.error("Error at argument " + a);
					System.exit(1);
				}

				options = new ArrayList<String>();
				params.put(a.substring(1), options);
			} else if (options != null) {
				options.add(a);
			} else {
				logger.error("Illegal parameter usage");
				System.exit(1);
			}
		}

		if (params.size() == 0) {
			printUsageWithError(null);
			System.exit(1);
		}

		if (params.get("l") != null)
			myIp = params.get("l").get(0);
		else {
			printUsageWithError("Provide this machine's external IP! (see ifconfig or your VM setup)");
			System.exit(1);			
		}

		if (params.get("p") != null)
			myPort = Integer.parseInt(params.get("p").get(0));
		else {
			printUsageWithError("Provide the port, that the middleware listens to (e.g. 11212)!");
			System.exit(1);			
		}

		if (params.get("m") != null) {
			mcAddresses = params.get("m");
		} else {
			printUsageWithError(
					"Give at least one memcached backend server IP address and port (e.g. 123.11.11.10:11211)!");
			System.exit(1);
		}

		if (params.get("t") != null)
			numThreadsPTP = Integer.parseInt(params.get("t").get(0));
		else {
			printUsageWithError("Provide the number of threads for the threadpool!");
			System.exit(1);
		}

		if (params.get("s") != null)
			readSharded = Boolean.parseBoolean(params.get("s").get(0));
		else {
			printUsageWithError("Provide true/false to enable sharded reads!");
			System.exit(1);
		}

	}

	private static void printUsageWithError(String errorMessage) {
		System.err.println();
		System.err.println(
				"Usage: -l <MyIP> -p <MyListenPort> -t <NumberOfThreadsInPool> -s <readSharded> -m <MemcachedIP:Port> <MemcachedIP2:Port2> ...");
		if (errorMessage != null) {
			logger.error("Error message: " + errorMessage);
		}

	}

	/***
	 * Request the system to shutdown gracefully
	 */
	public static void shutdown() {
		logger.info("Shutting down middleware...");
		sockHandler.shutdown();
		threadPool.shutdown();
		
		try {
			boolean isTerminated = threadPool.awaitTermination(THREADPOOL_AWAIT_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			if(isTerminated) {
				List<Runnable> droppedTasks = threadPool.shutdownNow();
            	logger.info("Thread pool was abruptly shut down. " + droppedTasks.size() + " requests will not be executed.");
			}
			else
				logger.debug("threadpool has been terminated.");
		} catch (InterruptedException ex) {
			logger.catching(ex);
		}
		
		logger.info("Shutdown completed");
	}
}

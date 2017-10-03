package ch.ethz.asl.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;

/***
 * Thread that handles all network connections
 * @author Florian Chlan
 */
public class SocketsHandler implements Runnable {

	/***
	 * The maximum request size. Should at least hold a SET-request with a value of up to 1024B
	 */
	static final int MAX_REQUEST_LENGTH_BYTES = 1536; 
	
	/***
	 * The maximum number of readable channels in the queue.
	 * This is the de-facto maximum number of concurrent clients.
	 */
	static final int MAX_CHANNEL_QUEUE_CAPACITY = 4096;
	
	private final String myIp;
	private final int myPort;
	private Selector selector = null;
	private ServerSocketChannel serverSocket = null;
	
	/***
	 * Indicates whether the thread should continue to run.
	 */
	private volatile boolean shouldRun = true;
	private volatile boolean isRunning = false;
	private BlockingQueue<SelectionKey> channelQueue = new ArrayBlockingQueue<SelectionKey>(MAX_CHANNEL_QUEUE_CAPACITY, false);

	private static final Logger logger = LogManager.getLogger(SocketsHandler.class);
	
	public SocketsHandler(String myIp, int myPort) {
		this.myIp = myIp;
		this.myPort = myPort;
	}
	
	@Override
	public void run() {
		/* ServerSocket and Selector code taken from the following tutorial: 
		 * http://crunchify.com/java-nio-non-blocking-io-with-server-client-example-java-nio-bytebuffer-and-channels-selector-java-nio-vs-io/
		 */
		
		isRunning = true;
		logger.debug("Opening ServerSocket on {}:{}", myIp, myPort);
		try {
			// Selector: multiplexor of SelectableChannel objects
			synchronized(this) {
				selector = Selector.open(); // selector is open here
			}
			// ServerSocketChannel: selectable channel for stream-oriented listening sockets
			serverSocket = ServerSocketChannel.open();
			InetSocketAddress serverAddr = new InetSocketAddress(myIp, myPort);
	 
			// Binds the channel's socket to a local address and configures the socket to listen for connections
			serverSocket.bind(serverAddr);
	 
			// Adjusts this channel's blocking mode.
			serverSocket.configureBlocking(false);
	 
			int ops = serverSocket.validOps();
			serverSocket.register(selector, ops, null);

			logger.info("ServerSocket opened. Listening for incoming connections...");
	
			// Infinite loop..
			// Keep server running
			while (shouldRun) {
	 
				// Selects a set of keys whose corresponding channels are ready for I/O operations
				selector.select();
				
				// token representing the registration of a SelectableChannel with a Selector
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iter = keys.iterator();
	 
				while (iter.hasNext()) {
					SelectionKey key = iter.next();
	 
					// Tests whether this key's channel is ready to accept a new socket connection
					if (key.isValid() && key.isAcceptable()) {
						SocketChannel client = serverSocket.accept();
	 
						try {
							// Adjusts this channel's blocking mode to false
							client.configureBlocking(false);
		 
							// Operation-set bit for read operations
							client.register(selector, SelectionKey.OP_READ);
							logger.debug("New connection accepted: " + client.getLocalAddress());
						} catch(Exception ex) {
							// Exception occured in a specific server socket.
							// Close it and continue!
							logger.catching(ex);
							client.close();
						}
	 
						// Tests whether this key's channel is ready for reading
					} else if (key.isValid() && key.isReadable()) {
						
						// Here we only figured out whether a client has data to read.
						// The parsing of the message will not be done in the network thread.
						// We will do this in the workers.
						
						// Stop polling on this client connection while it's waiting 
						// in the queue and is being processed.
						enqueueChannel(key);
						
							
						
						
					}
					iter.remove();
				}
			}
			
			// We exited the while-loop as we have been asked to shutdown.
			assert(shouldRun == false);
			try {
				selector.close();
				serverSocket.close();
				logger.debug("Sockets shutdown.");
			} catch (IOException ex) {
				// Nothing else we can do here
				logger.catching(ex);
			}
			logger.info("SocketsHandler stopped.");
			
		} catch (Exception ex) {
			// An exception occurred in the network thread.
			// We can't recover from that. Shutdown!
			logger.catching(ex);
			RunMW.shutdown();
		} finally {
			isRunning = false;
			
		}

	}

	/***
	 * Adds the channel to the request queue and
	 * stops the selector from checking this channel.
	 * Assume that the given channel is valid and has data to read
	 * @param key A key to the underlying channel
	 */
	private void enqueueChannel(SelectionKey key) {
		try {
			channelQueue.put(key);
			key.interestOps(0); // Stop the selector from checking this channel temporarily while it's request is being handled.
		} catch (InterruptedException ex) {
			// We got interrupted while waiting for a space in the queue to become available.
			// This could occur when shutting down the system. Nothing else to do here.
			logger.catching(ex);
		}
		
		
	}
	
	public BlockingQueue<SelectionKey> getChannelQueue() {
		return channelQueue;
	}
	
	/***
	 * Request the system to shutdown the server socket
	 * and stop accepting new connections
	 */
	public void closeServerSocket() {
		try {
			serverSocket.close();
			logger.debug("Server socket closed.");
		} catch (IOException ex) {
			// Nothing else we can do here
			logger.catching(ex);
		}
	}
	
	/***
	 * Indicates to this thread that it should stop executing and shutdown gracefully.
	 */
	public void shutdown() {
		shouldRun = false;
		synchronized(this) {
			if(selector != null) selector.wakeup();
		}
		logger.debug("Shutdown of SocketsHandler requested.");
	}

	public boolean isRunning() {
		return isRunning;
	}

}

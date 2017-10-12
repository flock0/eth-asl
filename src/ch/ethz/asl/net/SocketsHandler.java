package ch.ethz.asl.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.worker.Request;
import ch.ethz.asl.worker.RequestFactory;
import ch.ethz.asl.worker.Worker;

/***
 * Thread that handles all network connections
 * @author Florian Chlan
 */
public class SocketsHandler implements Runnable {
	
	private final String myIp;
	private final int myPort;
	private Selector selector = null;
	private ServerSocketChannel serverSocket = null;
	private HashMap<SelectionKey, ByteBuffer> clientBuffers;
	/***
	 * Indicates whether the thread should continue to run.
	 */
	private volatile boolean shouldRun = true;
	private volatile boolean isRunning = false;



	private ExecutorService threadPool;

	private static final Logger logger = LogManager.getLogger(SocketsHandler.class);
	
	public SocketsHandler(String myIp, int myPort, ExecutorService threadPool) {
		this.myIp = myIp;
		this.myPort = myPort;
		this.threadPool = threadPool;
	}
	
	@Override
	public void run() {
		/* ServerSocket and Selector code taken from the following tutorial: 
		 * http://crunchify.com/java-nio-non-blocking-io-with-server-client-example-java-nio-bytebuffer-and-channels-selector-java-nio-vs-io/
		 */
		
		isRunning = true;
		logger.debug(String.format("Opening ServerSocket on %s:%d", myIp, myPort));
		try {
			
			clientBuffers = new HashMap<>();
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
							
							// Initialize a ByteBuffer for the new client
							clientBuffers.put(key, ByteBuffer.allocate(Request.MAX_SIZE));
							
						} catch(Exception ex) {
							// Exception occured in a specific server socket.
							// Close it and continue!
							logger.catching(ex);
							client.close();
						}
	 
						// Tests whether this key's channel is ready for reading
					} else if (key.isValid() && key.isReadable()) {
						
						// Make sure there's space in the buffer
						ByteBuffer buffer = clientBuffers.get(key);
						if(!buffer.hasRemaining()) {
							logger.debug("Request buffer is full, but there's more to read!");
							throw new Exception("Request buffer is full, but there's more to read!");
						}
						
						// Read from the client into the client-specific buffer
						SocketChannel client = (SocketChannel)key.channel();
						
						try  {
							int readReturnCode = client.read(buffer);
							
							// If the connection is closed, or we get an end-of-stream, get rid of the ByteBuffer.
							if(readReturnCode == -1) {
								// Reached end-of-stream
								evictClientBuffer(key);
							}
						} catch(ClosedChannelException ex) {
							logger.catching(ex);
							evictClientBuffer(key);
						}
						
						// TODO Check if the request is valid
						Request req;
						try {
							req = RequestFactory.createRequest(buffer);
							// If valid, forward the request to the workers. Create request and copy out of ByteBuffer
							enqueueChannel(key, buffer);
						} catch(FaultyRequestException ex) {
							// TODO If not and never will be, send an error message to the client
						} catch(IncompleteRequestException ex) {
							// TODO If not and might be in the future, continue reading
						}
						
						
						// TODO Clear byte buffer
						// It is not needed to change interestOps here, as the client should wait after a valid request
						// Just make sure the ByteBuffer is cleared
						// TODO Still we should have a check whether we are reading from a channel that is currently being processed.
						//      Maybe a HashMap of Key to bool with a simple volatile flag.
						
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
	 * @param req The successfully parsed Request object
	 */
	private void enqueueChannel(SelectionKey key, Request req) {
		threadPool.submit(new Worker(key, req));
	
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
	
	public void wakeupSelector() {
		selector.wakeup();
	}

}

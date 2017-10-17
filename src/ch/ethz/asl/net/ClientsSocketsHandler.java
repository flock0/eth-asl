package ch.ethz.asl.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.worker.FaultyRequestException;
import ch.ethz.asl.worker.IncompleteRequestException;
import ch.ethz.asl.worker.Request;
import ch.ethz.asl.worker.RequestFactory;
import ch.ethz.asl.worker.Worker;

/***
 * Thread that handles all network connections
 * @author Florian Chlan
 */
public class ClientsSocketsHandler implements Runnable {
	
	private final String myIp;
	private final int myPort;
	private Selector selector = null;
	private ServerSocketChannel serverSocket = null;
	private HashMap<SelectionKey, ByteBuffer> clientReadBuffers;
	/***
	 * Indicates whether the thread should continue to run.
	 */
	private volatile boolean shouldRun = true;
	private volatile boolean isRunning = false;



	private ExecutorService threadPool;
	private Set<SelectableChannel> clientsToClose;

	private static final Logger logger = LogManager.getLogger(ClientsSocketsHandler.class);
	
	public ClientsSocketsHandler(String myIp, int myPort, ExecutorService threadPool) {
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
			
			clientReadBuffers = new HashMap<>();
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
							logger.error("Exception occured during accepting of "
									+ "new client onnection with the following message: " + ex.getMessage());
							client.close();
						}
	 
						// Tests whether this key's channel is ready for reading
					} else if (key.isValid() && key.isReadable()) {
						
						// Initialize a ByteBuffer for the new client
						if(!clientReadBuffers.containsKey(key)) {
							clientReadBuffers.put(key, ByteBuffer.allocate(Request.MAX_REQUEST_SIZE));
						}
						
						// Make sure there's space in the buffer
						
						ByteBuffer readBuffer = clientReadBuffers.get(key);
						if(!readBuffer.hasRemaining()) {
							logger.debug("Request buffer is full, but there's more to read!");
							throw new Exception("Request buffer is full, but there's more to read!");
						}
						
						// Read from the client into the client-specific buffer
						SocketChannel client = (SocketChannel)key.channel();
						
						try  {
							int readReturnCode = client.read(readBuffer);
							
							// If the connection is closed, or we get an end-of-stream, get rid of the ByteBuffer.
							if(readReturnCode == -1) {
								// Reached end-of-stream
								evictClientBuffer(key);
								client.close();
							}
							else {
								// TODO Check if the request is valid
								Request req;
								try {
									req = RequestFactory.tryParseClientRequest(readBuffer);
									// If valid, forward the request to the workers. Create request and copy out of ByteBuffer
									enqueueChannel(client, req);
									
									
								} catch(FaultyRequestException ex) {
									// TODO If not valid and never will be, send an error message to the client
									readBuffer.clear();
									sendClientError(client, ex.getMessage());
								} catch(IncompleteRequestException ex) {
									// If the request is incomplete, continue reading
								}
							}
						} catch(IOException ex) {
							logger.info("Encountered exception when communicating with client. Closing connection.");
							client.close();
							evictClientBuffer(key);
						}

						// It is not needed to change interestOps here, as the client should wait after a valid request
						// TODO Still we should have a check whether we are reading from a channel that is currently being processed.
						//      Maybe a HashMap of Key to a volatile bool flag
						
					}
					iter.remove();
				}
			}
			
			// We exited the while-loop as we have been asked to shutdown.
			assert(shouldRun == false);
			try {
				clientsToClose = new HashSet<SelectableChannel>();
				selector.keys().forEach(key -> clientsToClose.add(key.channel()));
				selector.close();
				serverSocket.close();
				logger.debug("Sockets shutdown.");
			} catch (IOException ex) {
				// Nothing else we can do here
				logger.error("Error occured during sockets and selector shutdown");
			}
			logger.info("SocketsHandler stopped.");
			
		} catch (Exception ex) {
			// An exception occurred in the network thread.
			// We can't recover from that. Shutdown!
			logger.error("Exception occured in network thread: " + ex.getMessage());
			logger.catching(ex);
			RunMW.shutdown();
		} finally {
			isRunning = false;
			
		}

	}

	private void sendClientError(SocketChannel client, String errorMessage) throws IOException {
		ByteBuffer errorBuffer = ByteBuffer.wrap(("CLIENT_ERROR" + errorMessage + "\r\n").getBytes());
		do {
			client.write(errorBuffer);
		} while(errorBuffer.hasRemaining());
	}

	/***
	 * Adds the channel to the request queue and
	 * stops the selector from checking this channel.
	 * Assume that the given channel is valid and has data to read
	 * @param client The channel to the client
	 * @param req The successfully parsed Request object
	 */
	private void enqueueChannel(SocketChannel client, Request req) {
		threadPool.submit(new Worker(client, req));
	
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
		closeServerSocket();
		shouldRun = false;
		synchronized(this) {
			if(selector != null) selector.wakeup();
		}
		logger.debug("Shutdown of SocketsHandler requested.");
	}

	public boolean isRunning() {
		return isRunning;
	}

	private void evictClientBuffer(SelectionKey key) {
		clientReadBuffers.remove(key);
	}

	public void closeClientSockets() {
		clientsToClose.forEach(client -> {
			try {
				client.close();
			} catch (IOException ex) {
				logger.catching(ex);
			}
		});
	}
}

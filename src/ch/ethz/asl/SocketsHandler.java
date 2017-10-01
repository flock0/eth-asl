package ch.ethz.asl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/***
 * Thread that handles all network connections
 * @author Florian Chlan
 */
public class SocketsHandler implements Runnable {

	static final int MAX_REQUEST_LENGTH_BYTES = 1280; // Set maximum request size that has more than enough space for a value of 1024B
	final String myIp;
	final int myPort;
	final List<String> mcAddresses;
	final int numThreadsPTP;
	final boolean readSharded;
	private Selector selector = null;
	private BlockingQueue<ReadableChannel> channelQueue = new BlockQueue<ReadableChannel>();
	private static final Logger logger = LogManager.getLogger(SocketsHandler.class);
	
	public SocketsHandler(String myIp, int myPort, List<String> mcAddresses, int numThreadsPTP, boolean readSharded) {
		this.myIp = myIp;
		this.myPort = myPort;
		this.mcAddresses = mcAddresses;
		this.numThreadsPTP = numThreadsPTP;
		this.readSharded = readSharded;
	}
	
	@Override
	public void run() {
		/* ServerSocket and Selector code taken from the following tutorial: 
		 * http://crunchify.com/java-nio-non-blocking-io-with-server-client-example-java-nio-bytebuffer-and-channels-selector-java-nio-vs-io/
		 */
		
		logger.debug("Opening ServerSocket on {}:{}", myIp, myPort)
		// Selector: multiplexor of SelectableChannel objects
		selector = Selector.open(); // selector is open here
 
		// ServerSocketChannel: selectable channel for stream-oriented listening sockets
		ServerSocketChannel serverSocket = ServerSocketChannel.open();
		InetSocketAddress serverAddr = new InetSocketAddress(myIp, myPort);
 
		// Binds the channel's socket to a local address and configures the socket to listen for connections
		serverSocket.bind(serverAddr);
 
		// Adjusts this channel's blocking mode.
		serverSocket.configureBlocking(false);
 
		int ops = serverSocket.validOps();
		SelectionKey selectKy = serverSocket.register(selector, ops, null);
 
		logger.info("ServerSocket opened. Listening for incoming connections...");

		// Infinite loop..
		// Keep server running
		while (true) {
 
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
 
					// Adjusts this channel's blocking mode to false
					client.configureBlocking(false);
 
					// Operation-set bit for read operations
					client.register(selector, SelectionKey.OP_READ);
					logger.debug("New connection accepted: " + client.getLocalAddress());
 
					// Tests whether this key's channel is ready for reading
				} else if (key.isValid() && key.isReadable()) {
					
					// Here we only figured out whether a client has data to read.
					// The parsing of the message will not be done in the network thread.
					// We will do this in the workers.
					
					// Stop polling on this client connection while it's waiting 
					// in the queue and is being processed.
					key.cancel(); 
					forwardToQueue(key);
					
						
					
					
				}
				iter.remove();
			}
		}

	}

	/***
	 * Forward the channel to the request queue.
	 * Assume that athe given channel is valid and has data to read
	 * @param key A key to the underlying channel
	 */
	private void forwardToQueue(SelectionKey key) {
		//TODO
		
	}

}

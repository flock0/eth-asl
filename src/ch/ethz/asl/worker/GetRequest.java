package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

/**
 * Contains a GET request with a single key.
 * @author Florian Chlan
 *
 */
public class GetRequest extends Request {

	private static final Logger logger = LogManager.getLogger(GetRequest.class);
	
	ByteBuffer readBuffer;
	String key;
	private int targetServerIndex;
	
	public GetRequest(ByteBuffer readBuffer) {
		super();
		this.readBuffer = readBuffer;
		this.numKeysRequested = 1;
	}

	public String getKey() {
		return key;
	}

	public byte[] getCommand() {
		int messageLength = readBuffer.remaining();
		byte[] arr = new byte[messageLength];
		readBuffer.get(arr);
		readBuffer.position(0);
		return arr;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		
		parseMessage();
		
		// Hash key to find server to handle
		targetServerIndex = HashingLoadBalancer.findTargetServer(key);
		
		// Send GET request to designated server
		
		setRequestSize(readBuffer.limit());
		setBeforeSendTime();
		try {
			memcachedSocketHandler.sendToSingleServer(readBuffer, targetServerIndex);
		} catch (IOException ex) {
			logger.error(String.format("%s encountered an error when sending GetRequest to memcached server: %s", Thread.currentThread().getName(), ex.getMessage()));
			logger.catching(ex);
			RunMW.shutdown();
		} finally {
			readBuffer.clear();
		}
		
		// Read from designated server
		ByteBuffer response = memcachedSocketHandler.waitForSingleResponse(targetServerIndex);
		setAfterReceiveTime(); // Caveat: We don't log the time if an error in the wait-method leads to a shutdown

		setResponseSize(response.limit());
		gatherCatchHitStatistic(response);
		
		try {	
			// Forward answerbuffer to client
			sendResponseToClient(client, response);
			
		} catch (IOException ex) {
			logger.error(String.format("%s couldn't handle GetRequest. Will close client connection: %s", Thread.currentThread().getName(), ex.getMessage()));
			try {
				client.close();
			} catch (IOException ex2) {
				// Nothing we can do here
				logger.catching(ex2);
			}
		} finally {
			if(response != null) response.clear();
		}
	}

	private void gatherCatchHitStatistic(ByteBuffer buffer) {
		if((char)buffer.get(0) == 'V')
			setNumHits(1);
	}

	private void parseMessage() {
		// They key appears after the "get ". The readBuffer will be at the limit position after the \r\n.
		this.key = new String(readBuffer.array(), 4, readBuffer.limit() - 2 - 4); 
	}

	private void sendResponseToClient(SocketChannel client, ByteBuffer response) throws IOException {
		do {
			client.write(response);
		} while(response.hasRemaining());
		
	}

	@Override
	public String getRequestType() {
		return "GET";
	}

	@Override
	public int getFirstTargetServer() {
		// TODO Auto-generated method stub
		return targetServerIndex;
	}

	@Override
	public int getNumOfTargetServers() {
		return 1;
	}
}

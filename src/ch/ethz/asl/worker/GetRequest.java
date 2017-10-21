package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

public class GetRequest extends Request {

	private static final Logger logger = LogManager.getLogger(GetRequest.class);
	
	ByteBuffer readBuffer;
	String key;
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
		
		// TODO Hash key to find server to handle
		int targetServerIndex = memcachedSocketHandler.findTargetServer(key);
		
		// TODO Send getrequest to designated server
		
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
}

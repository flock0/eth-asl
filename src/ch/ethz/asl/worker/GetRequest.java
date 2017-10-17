package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

public class GetRequest implements Request {

	private static final Logger logger = LogManager.getLogger(GetRequest.class);
	
	ByteBuffer readBuffer;
	String key;
	public GetRequest(ByteBuffer readBuffer, String key) {
		this.readBuffer = readBuffer;
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public Object getCommand() {
		int messageLength = readBuffer.remaining();
		byte[] arr = new byte[messageLength];
		readBuffer.get(arr);
		readBuffer.position(0);
		return arr;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		// TODO Hash key to find server to handle
		int targetServerIndex = memcachedSocketHandler.findTargetServer(key);
		
		// TODO Send getrequest to designated server
		
		try {
			memcachedSocketHandler.sendToSingleServer(readBuffer, targetServerIndex);
			readBuffer.clear();
		} catch (IOException ex) {
			logger.error(String.format("%s encountered an error when sending GetRequest to memcached server: %s", Thread.currentThread().getName(), ex.getMessage()));
			logger.catching(ex);
			RunMW.shutdown();
		} finally {
			readBuffer.clear();
		}
		
		ByteBuffer response = null;
		try {
			// TODO read from designated server
			response = memcachedSocketHandler.waitForSingleResponse(targetServerIndex);
			
			// TODO Forward answerbuffer to client
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

	private void sendResponseToClient(SocketChannel client, ByteBuffer response) throws IOException {
		do {
			client.write(response);
		} while(response.hasRemaining());
		
	}
}

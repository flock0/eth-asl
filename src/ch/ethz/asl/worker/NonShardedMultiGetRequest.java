package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class NonShardedMultiGetRequest extends MultiGetRequest {

	private static final Logger logger = LogManager.getLogger(NonShardedMultiGetRequest.class);
	
	public NonShardedMultiGetRequest(ByteBuffer readBuffer, List<String> keys) {
		super(readBuffer, keys);
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		// TODO Hash key to find server to handle
		int targetServerIndex = memcachedSocketHandler.findTargetServer(keys);
		
		// TODO Send getrequest to designated server
		try {
			memcachedSocketHandler.sendToSingleServer(readBuffer, targetServerIndex);
			readBuffer.clear();
			
			// TODO read from designated server
			ByteBuffer response = memcachedSocketHandler.waitForSingleResponse(targetServerIndex);
			
			// TODO Forward answerbuffer to client
			sendResponseToClient(client, response);
			response.clear();
		} catch (IOException ex) {
			logger.error(String.format("%s couldn't handle MultiGetRequest. Will close client connection: %s", Thread.currentThread().getName(), ex.getMessage()));
			try {
				client.close();
			} catch (IOException ex2) {
				// Nothing we can do here
				logger.catching(ex2);
			}
		}

	}

	private void sendResponseToClient(SocketChannel client, ByteBuffer response) throws IOException {
		do {
			client.write(response);
		} while(response.hasRemaining());
		
	}
}

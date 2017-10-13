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
	
	public NonShardedMultiGetRequest(ByteBuffer commandBuffer, List<String> keys) {
		super(commandBuffer, keys);
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		// TODO Hash key to find server to handle
		int targetServerIndex = memcachedSocketHandler.findTargetServer(keys);
		
		// TODO Send getrequest to designated server
		try {
			memcachedSocketHandler.sendToSingleServer(commandBuffer, targetServerIndex);
			commandBuffer.clear();
			
			// TODO read from designated server
			ByteBuffer response = memcachedSocketHandler.waitForSingleResponse(targetServerIndex);
			
			// TODO Forward answerbuffer to client
			sendResponseToClient(client, response);
		} catch (IOException ex) {
			logger.catching(ex);
		}

	}

	private void sendResponseToClient(SocketChannel client, ByteBuffer response) throws IOException {
		do {
			client.write(response);
		} while(response.hasRemaining());
		
	}
}

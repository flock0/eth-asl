package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class GetRequest implements Request {

	private static final Logger logger = LogManager.getLogger(GetRequest.class);
	
	ByteBuffer commandBuffer;
	String key;
	public GetRequest(ByteBuffer commandBuffer, String key) {
		this.commandBuffer = commandBuffer;
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public Object getCommand() {
		int messageLength = commandBuffer.remaining();
		byte[] arr = new byte[messageLength];
		commandBuffer.get(arr);
		commandBuffer.position(0);
		return arr;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		// TODO Hash key to find server to handle
		int targetServerIndex = memcachedSocketHandler.findTargetServer(key);
		
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

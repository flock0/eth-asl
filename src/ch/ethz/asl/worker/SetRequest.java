package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class SetRequest implements Request {

	private static final Logger logger = LogManager.getLogger(SetRequest.class);
	private static ByteBuffer successBuffer = ByteBuffer.wrap(new String("STORED\r\n").getBytes());
	ByteBuffer commandBuffer;
	
	public SetRequest(ByteBuffer commandBuffer) {
		this.commandBuffer = commandBuffer;
	}

	@Override
	public byte[] getCommand() {
		int messageLength = commandBuffer.remaining();
		byte[] arr = new byte[messageLength];
		commandBuffer.get(arr);
		commandBuffer.position(0);
		return arr;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		
		List<String> errors = null;
		try {
			//Forward set command to all memcached servers
			memcachedSocketHandler.sendToAll(commandBuffer);
			commandBuffer.clear();
			//Wait for responses of all memcached servers
			List<String> responses = memcachedSocketHandler.waitForAllStringResponses();
			errors = getErrors(responses);
			
		} catch (IOException ex) {
			logger.catching(ex);
			if(errors == null)
				errors = new ArrayList<String>();
			errors.add(String.format("SERVER_ERROR Exception in middleware: %s\r\n", ex.getMessage()));
		}
		
		// If an error occured, forward one of the error messages
		try {
			if(errors.isEmpty())
				sendSuccessToClient(client);
			else
				sendSingleErrorMessage(client, errors.get(0));
		} catch (IOException ex) {
			logger.catching(ex);
		}
	}

	private List<String> getErrors(List<String> responses) {
		List<String> errors = new ArrayList<String>();
		for(String respo : responses) {
			if(respo.equals("ERROR\r\n") ||
			   respo.equals("NOT_STORED\r\n") || 
			   respo.startsWith("CLIENT_ERROR ") ||
			   respo.startsWith("SERVER_ERROR ")) {
				logger.error(String.format("Memcached server responded with error. Will forward to the client: %s", respo));
				errors.add(respo);
			}
			else if(!respo.equals("STORED\r\n")) {
				logger.error(String.format("Memcached server responded unexpectedly. Will forward to the client: %s", respo));
				errors.add(respo);
			}
		}
		
		return errors;
		
	}

	private void sendSuccessToClient(SocketChannel client) throws IOException {
		successBuffer.position(0);
		do {
			client.write(successBuffer);
		} while(successBuffer.hasRemaining());
		
	}
	
	private void sendSingleErrorMessage(SocketChannel client, String errorMessage) throws IOException {
		ByteBuffer errorBuffer = ByteBuffer.wrap(errorMessage.getBytes());
		do {
			client.write(errorBuffer);
		} while(errorBuffer.hasRemaining());
	}
}

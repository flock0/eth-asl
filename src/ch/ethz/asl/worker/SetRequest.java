package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class SetRequest implements Request {

	private static final Logger logger = LogManager.getLogger(SetRequest.class);
	private static ByteBuffer successBuffer = ByteBuffer.wrap(new String("STORED\r\n").getBytes());
	ByteBuffer readBuffer;
	
	public SetRequest(ByteBuffer readBuffer) {
		this.readBuffer = readBuffer;
	}

	@Override
	public byte[] getCommand() {
		int messageLength = readBuffer.remaining();
		byte[] arr = new byte[messageLength];
		readBuffer.get(arr);
		readBuffer.position(0);
		return arr;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		
		List<String> errors = null;
		try {
			//Forward set command to all memcached servers
			memcachedSocketHandler.sendToAll(readBuffer);
			readBuffer.clear();
			//Wait for responses of all memcached servers
			List<String> responses = memcachedSocketHandler.waitForAllStringResponses();
			errors = getErrors(responses);
			
		} catch (IOException ex) {
			String errMsg = String.format("SERVER_ERROR Exception in middleware: %s\r\n", ex.getMessage());
			logger.error(errMsg);
			if(errors == null)
				errors = new ArrayList<String>();
			errors.add(errMsg);
			//TODO Not sure if we should abort here.
		}
		
		// If an error occured, forward one of the error messages
		try {
			if(errors.isEmpty())
				sendSuccessToClient(client);
			else
				sendSingleErrorMessage(client, errors.get(0));
		} catch (IOException ex) {
			logger.error(String.format("%s couldn't send SetRequest response to client. Will close client connection: %s", Thread.currentThread().getName(), ex.getMessage()));
			try {
				client.close();
			} catch (IOException ex2) {
				// Nothing we can do here
				logger.catching(ex2);
			}
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

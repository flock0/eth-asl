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
	byte[] command;
	ByteBuffer commandBuffer;
	
	public SetRequest(byte[] command) {
		this.command = command;
		this.commandBuffer = ByteBuffer.wrap(command);
	}

	@Override
	public byte[] getCommand() {
		return command;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client, ByteBuffer clientBuff) {
		
		
		//TODO Forward set command to all memcached servers
		memcachedSocketHandler.sendToAll(commandBuffer);
		
		
		
		//TODO Wait for responses of all memcached servers
		List<String> responses = memcachedSocketHandler.waitForAllResponses();

		List<String> errors = getErrors(responses);
		if(errors.isEmpty())
			sendSuccessToClient(client);
		else
			sendSingleErrorMessage(client, errors);
	}

	private List<String> getErrors(List<String> responses) {
		List<String> errors = new ArrayList<String>();
		for(String respo : responses) {
			if(respo.equals("ERROR\r\n") ||
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
		successBuffer.clear();
		do {
			client.write(successBuffer);
		} while(successBuffer.hasRemaining());
		
	}
	
	private void sendSingleErrorMessage(SocketChannel client, List<String> errors) throws IOException {
		ByteBuffer errorBuffer = ByteBuffer.wrap(errors.get(0).getBytes());
		do {
			client.write(errorBuffer);
		} while(errorBuffer.hasRemaining());
	}
}

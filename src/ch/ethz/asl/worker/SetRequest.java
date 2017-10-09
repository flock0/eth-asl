package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class SetRequest implements Request {

	private static final Logger logger = LogManager.getLogger(SetRequest.class);
	
	byte[] command;
	ByteBuffer commandBuffer;
	int commandLength;
	
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
		for(SocketChannel serverChannel : memcachedSocketHandler.getChannels()) {
			do {
				serverChannel.write(commandBuffer);
			} while(commandBuffer.hasRemaining());
			
			commandBuffer.position(0); // We send the same message to all memcached servers, hence a simple reset of the position.
		}
		
		//TODO Wait for responses of all memcached servers
		List<String> responses = memcachedSocketHandler.waitForAllResponses();
		for(SocketChannel serverChannel : memcachedSocketHandler.getChannels()) {
		
		}

		List<String> errors = getErrors(responses);
		if(errors.isEmpty())
			sendSuccessToClient(client, clientBuff);
		else
			sendSingleErrorMessage(errors);
			
		
		// TODO Auto-generated method stub. Remove code below
		logger.debug("set request");
		String answer = "STORED\r\n";
		byte[] answerBytes = answer.getBytes();
		buff.put(answerBytes);
		buff.flip();
		int sentBytes = 0;
		try {
			do {	
					sentBytes += client.write(buff);	
			} while(sentBytes < answerBytes.length);
		} catch (IOException ex) {
			logger.catching(ex);
		}
	}

}

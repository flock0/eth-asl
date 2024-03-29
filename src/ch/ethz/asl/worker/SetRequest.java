package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

/**
 * Contains a SET request.
 * @author Florian Chlan
 *
 */
public class SetRequest extends Request {

	private static final Logger logger = LogManager.getLogger(SetRequest.class);
	ByteBuffer readBuffer;
	
	public SetRequest(ByteBuffer readBuffer) {
		super();
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
		
		int numServers = MemcachedSocketHandler.getNumServers();
		String error = null;
		boolean errorOccured = false;
		ByteBuffer firstPositiveResponseBuffer = null;
		try {
			setRequestSize(readBuffer.limit());
			setBeforeSendTime();
			//Forward set command to all memcached servers
			memcachedSocketHandler.sendToAll(readBuffer);
			readBuffer.clear();

			ByteBuffer responseBuffer;
			boolean isFirstPositiveReponse = true;
			for(int serverIndex = 0; serverIndex < numServers; serverIndex++ ) {
				
				// Wait for answers from those servers we sent stuff to!
				responseBuffer = memcachedSocketHandler.waitForSingleResponse(serverIndex);
				
				if(!errorOccured) {
					// No error occured yet. We will continue to parse and add responses.
					error = getError(responseBuffer);
					if(responseContainsError(error)) {
						// One of the servers encountered an error.
						// We forward the error message and abort this request
						errorOccured = true;
					} else if(isFirstPositiveReponse) {
						firstPositiveResponseBuffer = responseBuffer;
						isFirstPositiveReponse = false;
					} else {
						responseBuffer.clear();
					}
				} else {
					responseBuffer.clear();
				}
				
				
			}
		
		} catch (IOException ex) {
			String errMsg = String.format("SERVER_ERROR Exception in middleware: %s\r\n", ex.getMessage());
			logger.error(errMsg);
			errorOccured = true;
			error = errMsg;
		} finally {
			setAfterReceiveTime();
		}
		
		// If an error occured, forward one of the error messages
		try {
			if(!errorOccured) {
				setResponseSize(firstPositiveResponseBuffer.limit());
				sendSuccessToClient(client, firstPositiveResponseBuffer);
			} else {
				setResponseSize(error.length());
				sendSingleErrorMessage(client, error);
			}
		} catch (IOException ex) {
			logger.error(String.format("%s couldn't send SetRequest response to client. Will close client connection: %s", Thread.currentThread().getName(), ex.getMessage()));
			try {
				client.close();
			} catch (IOException ex2) {
				// Nothing we can do here
				logger.catching(ex2);
			}
		} finally {
			if(firstPositiveResponseBuffer != null) firstPositiveResponseBuffer.clear();
		}
	}

	private boolean responseContainsError(String error) {
		return error != null;
	}

	private String getError(ByteBuffer buffer) {
		
		int messageLength = buffer.remaining();
		String error = null;
		
		char firstChar = (char)buffer.get(0);
		char secondChar = (char)buffer.get(1);
		if((firstChar == 'E' && secondChar == 'R') ||
			(firstChar == 'C' && secondChar == 'L') ||
			(firstChar == 'S' && secondChar == 'E') ||
			(firstChar == 'N' && secondChar == 'O')) {
			error = new String(buffer.array(), 0, messageLength);
			logger.error(String.format("Memcached server responded with error. Will forward to the client: %s", error));
		}
		else if(firstChar != 'S' || secondChar != 'T') {
			error = new String(buffer.array(), 0, messageLength);
			logger.error(String.format("Memcached server responded unexpectedly. Will forward to the client: %s", error));
		}
		
		return error;
	}

	private void sendSuccessToClient(SocketChannel client, ByteBuffer buffer) throws IOException {
		do {
			client.write(buffer);
		} while(buffer.hasRemaining());
		
	}
	
	private void sendSingleErrorMessage(SocketChannel client, String errorMessage) throws IOException {
		ByteBuffer errorBuffer = ByteBuffer.wrap(errorMessage.getBytes());
		do {
			client.write(errorBuffer);
		} while(errorBuffer.hasRemaining());
	}

	@Override
	public String getRequestType() {
		return "SET";
	}

	@Override
	public int getFirstTargetServer() {
		return -1;
	}

	@Override
	public int getNumOfTargetServers() {
		// TODO Auto-generated method stub
		return MemcachedSocketHandler.getNumServers();
	}
}

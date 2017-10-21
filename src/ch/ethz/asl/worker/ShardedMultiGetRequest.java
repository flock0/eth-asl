package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

public class ShardedMultiGetRequest extends MultiGetRequest {

	private static final Logger logger = LogManager.getLogger(ShardedMultiGetRequest.class);
	 
	String[] keys;
	public ShardedMultiGetRequest(ByteBuffer readBuffer, int numKeysReceived) {
		super(readBuffer, numKeysReceived);
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		
		parseMessage();
		this.keys = splitUpKeys(keysString);
		
		// TODO Split up keys to available servers
		readBuffer.clear();
		int startingServerIndex = memcachedSocketHandler.findTargetServer(keysString);
		int numServers = MemcachedSocketHandler.getNumServers();
		
		List<Integer> answerAssemblyOrder = new ArrayList<Integer>();
		HashMap<Integer, List<String>> keySplits = new HashMap<>();
		int maxKeysPerServer = (int) Math.ceil((double)keys.length / numServers);
		
		int currentServer = startingServerIndex;
		int keyPerServerCount = 0;
		for(String key : keys) {
			if(!keySplits.containsKey(currentServer)) {
				answerAssemblyOrder.add(currentServer);
				keySplits.put(currentServer, new ArrayList<>());
			}
			keySplits.get(currentServer).add(key);
			
			if(++keyPerServerCount == maxKeysPerServer) {
				currentServer = (currentServer + 1) % numServers;
				keyPerServerCount = 0;
			}
		}
		
		
		// Construct separate multigets
		HashMap<Integer, ByteBuffer> serverBuffers = null;
		try {
			serverBuffers = memcachedSocketHandler.getServerBuffers();
			setBeforeSendTime();
			for(int serverIndex : answerAssemblyOrder) {
				ByteBuffer buffer = RequestFactory.constructMultiGetRequest(keySplits.get(serverIndex), serverBuffers.get(serverIndex));
				
				// Send multigets to the servers
				memcachedSocketHandler.sendToSingleServer(buffer, serverIndex);
				buffer.clear();
			}
		} catch (IOException ex) {
			logger.error(String.format("%s encountered an error when sending to a memcached server: %s", Thread.currentThread().getName(), ex.getMessage()));
			logger.catching(ex);
			RunMW.shutdown();
		}
		
			
		
		boolean errorOccured = false;
		boolean firstIteration = true;
		ByteBuffer finalResponseBuffer = null; // We use the serverBuffer from the first server to store the final response
		String error = null;
		for(int serverIndex : answerAssemblyOrder) {
			
			// Wait for answers from those servers we sent stuff to!
			ByteBuffer responseBuffer = memcachedSocketHandler.waitForSingleResponse(serverIndex);
			
			// If this is the first iteration, we save this buffer and append the answers from the other servers to it
			if(firstIteration) {
				finalResponseBuffer = responseBuffer;
			}
			
			if(!errorOccured) {
				// No error occured yet. We will continue to parse and add responses.
				error = getError(responseBuffer);
				if(responseContainsError(error)) {
					// One of the servers encountered an error.
					// We forward the error message and abort this request
					errorOccured = true;
				}
				else {
					if(firstIteration){
						firstIteration = false;
						finalResponseBuffer.position(finalResponseBuffer.limit());
						finalResponseBuffer.limit(finalResponseBuffer.capacity());
					}
					else {
						// If no error occured, gather responses, concat answer to final answer
						
						addResponseToFinalResponseBuffer(responseBuffer, finalResponseBuffer);
					}
				}
			}
			
		}
		
		setAfterReceiveTime();
		
		try {
			if(!errorOccured) {
				gatherCacheHitStatistic(finalResponseBuffer);
				sendFinalResponse(client, finalResponseBuffer);
			}
			else {
				sendErrorMessage(client, error);
			}
			
		} catch (IOException ex) {
			logger.error(String.format("%s couldn't handle MultiGetRequest. Will close client connection: %s", Thread.currentThread().getName(), ex.getMessage()));
			try {
				client.close();
			} catch (IOException ex2) {
				// Nothing we can do here
				logger.catching(ex2);
			}
		} finally {
			// Reset all serverBuffers for the next request
			for(ByteBuffer buffer : serverBuffers.values())
				buffer.clear();
		}
		
	}

	private String[] splitUpKeys(String keysString) {
		return keysString.split(" ");
	}

	private void sendFinalResponse(SocketChannel client, ByteBuffer buffer) throws IOException {
		buffer.flip();
		do {
			client.write(buffer);
		} while(buffer.hasRemaining());
	}

	private void addResponseToFinalResponseBuffer(ByteBuffer responseBuffer, ByteBuffer finalResponseBuffer) {
		// We have to get rid of the last END\r\n of each individual response. END\r\n is 5 bytes long.
		finalResponseBuffer.position(finalResponseBuffer.position() - 5);
		// Add the whole answer to the final response.
		finalResponseBuffer.put(responseBuffer);		
	}

	private void sendErrorMessage(SocketChannel client, String error) throws IOException {
		ByteBuffer errorBuffer = ByteBuffer.wrap(error.getBytes());
		do {
			client.write(errorBuffer);
		} while(errorBuffer.hasRemaining());
		
	}

	private boolean responseContainsError(String error) {
		return error != null;
	}

	private String getError(ByteBuffer buffer) {
		
		int messageLength = buffer.remaining();
		String error = null;
		
		char firstChar = (char)buffer.get(0);
		char secondChar = (char)buffer.get(1);
		if((firstChar == 'E' && secondChar == 'R') || //ERROR
		   firstChar == 'C' || //CLIENT_ERROR
		   firstChar == 'S') { //SERVER_ERROR
			error = new String(buffer.array(), 0, messageLength);
			logger.error(String.format("Memcached server responded with error. Will forward to the client: %s", error));
		}
		else if(!responseEndsWithEND(buffer, messageLength)) {
			error = new String(buffer.array(), 0, messageLength);
			logger.error(String.format("Memcached server responded unexpectedly. Will forward to the client: %s", error));
		}
		
		buffer.rewind();
		return error;
	}

	private boolean responseEndsWithEND(ByteBuffer buffer, int messageLength) {
		return (char)buffer.get(messageLength-5) == 'E' && (char)buffer.get(messageLength-4) == 'N' && (char)buffer.get(messageLength-3) == 'D' && (char)buffer.get(messageLength-2) == '\r' && (char)buffer.get(messageLength-1) == '\n';
	}

}

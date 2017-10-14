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

public class ShardedMultiGetRequest extends MultiGetRequest {

	private static final Logger logger = LogManager.getLogger(ShardedMultiGetRequest.class);
	
	public ShardedMultiGetRequest(ByteBuffer commandBuffer, List<String> keys) {
		super(commandBuffer, keys);
		commandBuffer.clear(); // As we construct our own get requests, we don't need the commandBuffer and can clear it already.
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		// TODO Split up keys to available servers
		List<List<String>> splits = splitKeys(keys, MemcachedSocketHandler.getNumServers());
		
		try {
			// TODO construct separate multigets
			int i = 0;
			HashMap<Integer, ByteBuffer> serverBuffers = memcachedSocketHandler.getServerBuffers();
			for(List<String> assignedKeys : splits) {
				ByteBuffer buffer = RequestFactory.constructMultiGetRequest(assignedKeys, serverBuffers.get(i));
				
				// TODO Send multigets to the servers
				memcachedSocketHandler.sendToSingleServer(buffer, i);
				
				i++;
			}
		} catch (IOException ex) {
			logger.catching(ex);
		}
		// TODO Wait for all responses
		HashMap<Integer, ByteBuffer> responseBuffers = // TODO Wait for answerse from those servers I sent stuff to!		
		List<String> errors = getErrors(responseBuffers);
		// TODO If an error occured, forward one of the error messages
		if(!errors.isEmpty()) {
			sendSingleErrorMessage(client, errors.get(0));
		}
		else {
			// TODO If no error occured, gather responses, reorder data blocks
			ByteBuffer finalResponse = reconstructFinalResponse(responseBuffers, splits);
			
			// TODO Send answer to client
			sendAnwswerToClient(finalResponse);
		}
		

	}

	private List<List<String>> splitKeys(List<String> keys, int numServers) {
		
		List<List<String>> serverList = new ArrayList<>();
		int maxKeysPerServer = (int) Math.ceil((double)keys.size() / numServers);
		
		
		for(int server = 0; server < numServers; server++) {
			serverList.add(new ArrayList<String>());
		}
		
		int currentServer = 0;
		int keyPerServerCount = 0;
		for(String key : keys) {
			serverList.get(currentServer).add(key);
			if(++keyPerServerCount == maxKeysPerServer) {
				currentServer++;
				keyPerServerCount = 0;
			}
		}
		
		return serverList;
	}

}

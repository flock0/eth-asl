package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class ShardedMultiGetRequest extends MultiGetRequest {

	public ShardedMultiGetRequest(ByteBuffer commandBuffer, List<String> keys) {
		super(commandBuffer, keys);
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		// TODO Split up keys to available servers
		List<List<String>> splits = splitKeys(keys, memcachedSocketHandler.getNumServers());
		
		// TODO construct separate multigets
		int i = 0;
		for(List<String> assignedKeys : splits) {
			ByteBuffer buffer = RequestFactory.constructMultiGetRequest(assignedKeys);
			
			// TODO Send multigets to the servers
			memcachedSocketHandler.sendToSingleServer(buffer, i);
			i++;
		}
		
		// TODO Wait for all responses
		HashMap<Integer, ByteBuffer> responseBuffers = memcachedSocketHandler.waitForAllResponses();		
		
		// TODO Reconstruct and reorder 
		// TODO If an error occured, forward one of the error messages
		// TODO If no error occured, gather responses, reorder data blocks
		// TODO Reconstruct final answer
		// TODO Send answer to client

	}

}

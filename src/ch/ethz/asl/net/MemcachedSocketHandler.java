package ch.ethz.asl.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MemcachedSocketHandler {
	
	private static final int SERVER_BUFFER_MAX_BYTES_SIZE = 3000; //Maximum size of the answer to a request
	
	private static final Logger logger = LogManager.getLogger(MemcachedSocketHandler.class);
	
	private static List<String> mcAddresses;
	private List<SocketChannel> channels = new ArrayList<SocketChannel>();
	private List<ByteBuffer> serverBuffers = new ArrayList<ByteBuffer>();
	
	 


	public static void setMcAddresses(List<String> mcAddresses) {
		MemcachedSocketHandler.mcAddresses = mcAddresses;
		logger.debug("Set new memcached addresses.");
	}
	
	public List<SocketChannel> getChannels() {
		return channels;
	}
	
	public MemcachedSocketHandler() {
		try {
			for(String addr : mcAddresses) {
				String[] splitAddr = addr.split(":");
				String host = splitAddr[0];
				int port = Integer.parseInt(splitAddr[1]);
				SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
				logger.debug(String.format("Connected to %s", addr));
				channels.add(channel);
				serverBuffers.add(ByteBuffer.allocate(SERVER_BUFFER_MAX_BYTES_SIZE));
			}
			logger.debug("Connected to all memcached servers.");
		} catch(IOException ex) {
			logger.catching(ex);
		}
	}

	public void sendToAll(ByteBuffer commandBuffer) throws IOException {
		for(SocketChannel server : channels) {
			do {
				server.write(commandBuffer);
			} while(commandBuffer.hasRemaining());
			
			commandBuffer.position(0); // We send the same message to all memcached servers, hence a simple reset of the position.
		}
		
	}

	public List<String> waitForAllResponses() {
		List<String> responses = new ArrayList<String>();
		
		for(int i = 0; i < channels.size(); i++) {
			SocketChannel server = channels.get(i);
			ByteBuffer buffer = serverBuffers.get(i);
			buffer.clear();
			
	    	int readReturnCode = -2; //Initially set to some unused code
	    	do {
	    		try {
					readReturnCode = server.read(buffer);
				} catch (IOException ex) {
					logger.catching(ex);
				}
	    	} while(readReturnCode != 0 && readReturnCode != -1); //TODO Handle overflow of buffer
	    	
	    	if(readReturnCode == -1)
				try {
					server.close();
					reconnectServer(i);
				} catch (IOException ex) {
					logger.catching(ex);
				}
			else {
				buffer.flip();
				int messageLength = buffer.remaining();
				byte[] arr = new byte[messageLength];
				buffer.get(arr);
				
				String response = new String(arr);
				responses.add(response);
			}
		}
		
		return responses;
	}

	private void reconnectServer(int index) {
		// TODO Remove socket channel on that index, try to reconnect and insert it into the right index position again
		// TODO If it still fails to connect, throw another exception
		throw new UnsupportedOperationException("Not yet implemented");
		
	}
	
}

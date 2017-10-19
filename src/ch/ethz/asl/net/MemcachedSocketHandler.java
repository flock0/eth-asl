package ch.ethz.asl.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;

public class MemcachedSocketHandler {
	
	private static final int SERVER_BUFFER_MAX_BYTES_SIZE = 11000; //Maximum size of the answer to a request
	
	private static final Logger logger = LogManager.getLogger(MemcachedSocketHandler.class);
	
	private static HashMap<Integer, String> mcAddresses = new HashMap<>();
	private static int numServers;
	private HashMap<Integer, SocketChannel> channels = new HashMap<>();
	private HashMap<Integer, ByteBuffer> serverBuffers = new HashMap<>();


	public static void setMcAddresses(List<String> mcAddresses) {
		numServers = mcAddresses.size();
		for(int i = 0; i < numServers; i++)
			MemcachedSocketHandler.mcAddresses.put(i, mcAddresses.get(i));
		
		logger.debug("Set new memcached addresses.");
	}
	
	
	public MemcachedSocketHandler() {
		try {
			for(int i = 0; i < numServers; i++) {
				String addr = mcAddresses.get(i);
				String[] splitAddr = addr.split(":");
				String host = splitAddr[0];
				int port = Integer.parseInt(splitAddr[1]);
				SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
				logger.debug(String.format("%s connected to %s.", Thread.currentThread().getName(), addr));
				channels.put(i, channel);
				serverBuffers.put(i, ByteBuffer.allocate(SERVER_BUFFER_MAX_BYTES_SIZE));
			}
			logger.debug(String.format("%s connected to all memcached servers.", Thread.currentThread().getName()));
		} catch(IOException ex) {
			logger.error(String.format("%s couldn't connect to all memcached server", Thread.currentThread().getName()));
			RunMW.shutdown();
		}
	}

	public void sendToAll(ByteBuffer commandBuffer) throws IOException {
		for(int i = 0; i < numServers; i++) {
			SocketChannel server = channels.get(i);
			do {
				server.write(commandBuffer);
			} while(commandBuffer.hasRemaining());
			
			commandBuffer.position(0); // We send the same message to all memcached servers, hence a simple reset of the position.
		}
		
	}

	public void sendToSingleServer(ByteBuffer commandBuffer, int targetServerIndex) throws IOException {
		SocketChannel server = channels.get(targetServerIndex);
		do {
			server.write(commandBuffer);
		} while(commandBuffer.hasRemaining());
	}
	
	public HashMap<Integer, ByteBuffer> waitForAllResponses() {
		try {
			for(int i = 0; i < channels.size(); i++) {
				SocketChannel server = channels.get(i);
				ByteBuffer buffer = serverBuffers.get(i);
				
				
		    	int readReturnCode = -2; //Initially set to some unused code
		    	do {
					readReturnCode = server.read(buffer);
		    	} while(readReturnCode != -1 && !receivedValidResponse(buffer)); //TODO Handle overflow of buffer
		    	
		    	if(readReturnCode == -1) {
					logger.error(String.format("%s lost connection to memcached server ", Thread.currentThread().getName(), i));
					server.close();
					RunMW.shutdown();
		    	}
				else {
					buffer.flip();
				}
			}
		} catch (IOException ex) {
			logger.error(String.format("%s encountered exception when reading from memcached servers", Thread.currentThread().getName()));
			RunMW.shutdown();
		}
		return serverBuffers;
	}
	
	public List<String> waitForAllStringResponses() {
		
		List<String> responses = new ArrayList<>();
		HashMap<Integer, ByteBuffer> responseBuffers = waitForAllResponses();
		for(int i = 0; i < numServers; i++) {
			ByteBuffer buffer = responseBuffers.get(i);
			int messageLength = buffer.remaining();
			byte[] arr = new byte[messageLength];
			buffer.get(arr);
			
			String response = new String(arr);
			responses.add(response);
			buffer.clear();
		}
		
		return responses;
		
	}
	
	public ByteBuffer waitForSingleResponse(int targetServerIndex) {
		
		SocketChannel server = channels.get(targetServerIndex);
		ByteBuffer buffer = serverBuffers.get(targetServerIndex);
		
		try {
	    	int readReturnCode = -2; //Initially set to some unused code
	    	do {
				readReturnCode = server.read(buffer);
	    	} while(readReturnCode != -1 && !receivedValidResponse(buffer)); //TODO Handle overflow of buffer
	    	
	    	if(readReturnCode == -1) {
				logger.error(String.format("%s lost connection to memcached server ", Thread.currentThread().getName(), targetServerIndex));
				server.close();
				RunMW.shutdown();
				return null;
			}
				
			buffer.flip();
		} catch (IOException ex) {
			logger.error(String.format("%s encountered exception when reading from memcached servers", Thread.currentThread().getName()));
			RunMW.shutdown();
		}
		return buffer;
	}
	
	private boolean receivedValidResponse(ByteBuffer buffer) {
		/* The following answers are expected:
		 * ERROR\r\n
		 * CLIENT_ERROR <custom text>\r\n
		 * SERVER_ERROR <custom text>\r\n
		 * STORED\r\n
		 * NOT_STORED\r\n
		 * VALUE...END\r\n
		 */
		
		boolean isValid = false;
		int oldPosition = buffer.position();
		
		byte[] msgArr = new byte[oldPosition];
		buffer.position(0);
		buffer.get(msgArr);
		String msg = new String(msgArr);
		if(msg.equals("ERROR\r\n") || msg.equals("STORED\r\n") || msg.equals("NOT_STORED\r\n")) {
			isValid = true;
		} 
		else if(msg.startsWith("CLIENT_ERROR ") || msg.startsWith("SERVER_ERROR ")) {
			if(msg.endsWith("\r\n")) {
				isValid = true;
			} else {
				isValid = false;
			}
		} 
		else if(msg.endsWith("END\r\n")) {
			isValid = true;
		}
		else {
			isValid = false;
		}			

		return isValid;
	}

	public int findTargetServer(String key) {
		return Math.floorMod(key.hashCode(), numServers);  
	}


	public int findTargetServer(List<String> keys) {
		return Math.floorMod(keys.get(0).hashCode(), numServers);
	}


	public static int getNumServers() {
		return numServers;
	}


	public HashMap<Integer, ByteBuffer> getServerBuffers() {
		return serverBuffers;
	}


	public void shutdown() {
		logger.debug(String.format("Shutting down memcached sockets in %s", Thread.currentThread().getName()));
		for(int i = 0; i < numServers; i++) {
			try {
				channels.get(i).close();
			} catch (IOException ex) {
				logger.catching(ex);
			}
		}
	}


	@Override
	protected void finalize() throws Throwable {
		shutdown();
		super.finalize();
	}
}

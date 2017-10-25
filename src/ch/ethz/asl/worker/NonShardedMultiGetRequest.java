package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

public class NonShardedMultiGetRequest extends MultiGetRequest {

	private static final Logger logger = LogManager.getLogger(NonShardedMultiGetRequest.class);

	private int targetServerIndex;
	
	public NonShardedMultiGetRequest(ByteBuffer readBuffer, int numKeysReceived) {
		super(readBuffer, numKeysReceived);
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client) {
		
		parseMessage();
		
		// TODO Hash key to find server to handle
		targetServerIndex = memcachedSocketHandler.findTargetServer(keysString);
		
		// TODO Send getrequest to designated server
		try {
			setRequestSize(readBuffer.limit());
			setBeforeSendTime();
			memcachedSocketHandler.sendToSingleServer(readBuffer, targetServerIndex);
		} catch (IOException ex) {
			logger.error(String.format("%s encountered an error when sending MultiGetRequest to memcached server: %s", Thread.currentThread().getName(), ex.getMessage()));
			logger.catching(ex);
			RunMW.shutdown();
		} finally {
			readBuffer.clear();
		}
		
		// TODO read from designated server
		ByteBuffer response = memcachedSocketHandler.waitForSingleResponse(targetServerIndex);
		setAfterReceiveTime();
		
		setResponseSize(response.limit());
		gatherCacheHitStatistic(response);
		try {
			// TODO Forward answerbuffer to client
			sendResponseToClient(client, response);
			
		} catch (IOException ex) {
			logger.error(String.format("%s couldn't handle MultiGetRequest. Will close client connection: %s", Thread.currentThread().getName(), ex.getMessage()));
			try {
				client.close();
			} catch (IOException ex2) {
				// Nothing we can do here
				logger.catching(ex2);
			}
		} finally {
			if(response != null) response.clear();
		}

	}

	private void sendResponseToClient(SocketChannel client, ByteBuffer response) throws IOException {
		do {
			client.write(response);
		} while(response.hasRemaining());
		
	}

	@Override
	public String getRequestType() {
		return "NonshardedGET";
	}

	@Override
	public int getFirstTargetServer() {
		return targetServerIndex;
	}

	@Override
	public int getNumOfTargetServers() {
		return 1;
	}
}

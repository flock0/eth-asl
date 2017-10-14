package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;

public interface Request {
	
	/***
	 * Maximum size of a request or answer is around 10200 bytes.
	 */
	static final int MAX_MESSAGE_SIZE = 11000;
	
	/***
	 * Maximum size  of datablocks in set commands is 1024 bytes.
	 */
	static final int MAX_DATABLOCK_SIZE = 1024;
	
	public Object getCommand();

	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client);
}

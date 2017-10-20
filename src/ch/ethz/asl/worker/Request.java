package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;

public abstract class Request {
	
	/***
	 * Maximum size of a request or answer is around 10200 bytes.
	 */
	public static final int MAX_REQUEST_SIZE = 3000;
	
	/***
	 * Maximum size  of datablocks in set commands is 1024 bytes.
	 */
	public static final int MAX_DATABLOCK_SIZE = 1024;
	
	private long nanotimeInitialized;
	private long millitimeInitialized;
	
	protected Request() {
		nanotimeInitialized = System.nanoTime();
		millitimeInitialized = System.currentTimeMillis();
	}
	
	public abstract byte[] getCommand();

	public abstract void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client);
	
}

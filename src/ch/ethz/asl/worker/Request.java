package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;

public interface Request {
	
	/***
	 * Maximum size of a get command is 2513 bytes.
	 */
	static final int MAX_SIZE = 3000;
	
	public Object getCommand();

	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client, ByteBuffer clientBuff);
}

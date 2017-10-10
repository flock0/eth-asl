package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;

public interface Request {
	public Object getCommand();

	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client, ByteBuffer clientBuff);
}

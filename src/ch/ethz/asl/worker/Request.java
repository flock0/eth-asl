package ch.ethz.asl.worker;

import java.nio.ByteBuffer;

import ch.ethz.asl.net.MemcachedSocketHandler;

public interface Request {
	public Object getCommand();

	public void handle(MemcachedSocketHandler memcachedSocketHandler, ByteBuffer clientBuff);
}

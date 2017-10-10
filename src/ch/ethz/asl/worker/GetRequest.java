package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class GetRequest implements Request {

	String command;
	String key;
	public GetRequest(String command, String key) {
		this.command = command;
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public Object getCommand() {
		return command;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client, ByteBuffer clientBuff) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Not yet implemented");
		
	}
}

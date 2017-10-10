package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class MultiGetRequest implements Request {

	String command;
	List<String> keys;
	public MultiGetRequest(String command, List<String> keys) {
		this.command = command;
		this.keys = keys;
	}

	@Override
	public Object getCommand() {
		return command;
	}
	
	public List<String> getKeys() {
		return keys;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client, ByteBuffer clientBuff) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Not yet implemented");
		
	}

	

}

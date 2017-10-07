package ch.ethz.asl.worker;

import java.nio.ByteBuffer;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class SetRequest implements Request {

	byte[] command;
	public SetRequest(byte[] command) {
		this.command = command;
	}

	@Override
	public byte[] getCommand() {
		return command;
	}

	@Override
	public void handle(MemcachedSocketHandler memcachedSocketHandler, ByteBuffer clientBuff) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Not yet implemented");
	}

}

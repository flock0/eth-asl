package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class MultiGetRequest implements Request {

	ByteBuffer readBuffer;
	String keysString;
	
	public MultiGetRequest(ByteBuffer readBuffer) {
		this.readBuffer = readBuffer;
	}

	@Override
	public byte[] getCommand() {
		int messageLength = readBuffer.remaining();
		byte[] arr = new byte[messageLength];
		readBuffer.get(arr);
		readBuffer.position(0);
		return arr;
	}
	
	public void parseMessage() {
		this.keysString = new String(readBuffer.array(), 4, readBuffer.limit() - 1 - 4);
	}

}

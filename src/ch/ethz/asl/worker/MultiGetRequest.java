package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class MultiGetRequest implements Request {

	ByteBuffer readBuffer;
	List<String> keys;
	public MultiGetRequest(ByteBuffer readBuffer, List<String> keys) {
		this.readBuffer = readBuffer;
		this.keys = keys;
	}

	@Override
	public byte[] getCommand() {
		int messageLength = readBuffer.remaining();
		byte[] arr = new byte[messageLength];
		readBuffer.get(arr);
		readBuffer.position(0);
		return arr;
	}
	
	public List<String> getKeys() {
		return keys;
	}

}

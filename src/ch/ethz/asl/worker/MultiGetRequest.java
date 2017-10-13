package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class MultiGetRequest implements Request {

	ByteBuffer commandBuffer;
	List<String> keys;
	public MultiGetRequest(ByteBuffer commandBuffer, List<String> keys) {
		this.commandBuffer = commandBuffer;
		this.keys = keys;
	}

	@Override
	public Object getCommand() {
		int messageLength = commandBuffer.remaining();
		byte[] arr = new byte[messageLength];
		commandBuffer.get(arr);
		commandBuffer.position(0);
		return arr;
	}
	
	public List<String> getKeys() {
		return keys;
	}

}

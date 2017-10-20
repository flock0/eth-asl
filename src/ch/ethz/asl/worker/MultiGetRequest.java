package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class MultiGetRequest extends Request {

	ByteBuffer readBuffer;
	String keysString;
	
	public MultiGetRequest(ByteBuffer readBuffer) {
		super();
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
		this.keysString = new String(readBuffer.array(), 4, readBuffer.limit() - 2 - 4);
	}

	public List<String> getKeys() {
		parseMessage();
		List<String> res = new ArrayList<String>();
		for(String key : keysString.split(" ")) {
			res.add(key);
		}
		return res;
	}

}

package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.RunMW;

public abstract class MultiGetRequest extends Request {

	ByteBuffer readBuffer;
	String keysString;
	
	private static final Logger logger = LogManager.getLogger(MultiGetRequest.class);
	
	public MultiGetRequest(ByteBuffer readBuffer, int numKeysRequested) {
		super();
		this.readBuffer = readBuffer;
		this.numKeysRequested = numKeysRequested;
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

	protected void gatherCacheHitStatistic(ByteBuffer buffer) {
		int hitCounter = 0;
		int currentPos = 0;
		String msg = new String(buffer.array(), currentPos, 5);
		while(!msg.equals("END\r\n")) {
			
			if(msg.equals("VALUE"))
				hitCounter++;

			
			findNextVALUE(buffer);
			
			currentPos = buffer.position();
			msg = new String(buffer.array(), currentPos, 5);
		}
		
		buffer.rewind();
		setNumHits(hitCounter);
	}

	private boolean findNextVALUE(ByteBuffer buffer) {
		
		int whitespaceCount = 0;
		int numBytesPos = -1;
		boolean newlineFound = false;
	    while(!newlineFound && buffer.hasRemaining()) {
	    	
	    	char currentChar = (char)buffer.get();
		    if(currentChar == '\r' && (char)buffer.get() == '\n') {
			    newlineFound = true;
		    }
		    else {
		    	// This position reset is probably not needed, as an \r will probably not appear in the String line of the responses
		    	//readBuffer.position(readBuffer.position() - 1);
		    	if(currentChar == ' ') {
	                whitespaceCount++;
	                if(whitespaceCount == 3)
		            	numBytesPos = buffer.position();
	            }
		    }
	    }
	    
	    // Parse size of data block
    	String numBytesText = new String(buffer.array(), numBytesPos, buffer.position() - numBytesPos - 2);
    	int datablockSize = 0;
    	try {
    		datablockSize = Integer.parseInt(numBytesText);
	    } catch(NumberFormatException ex) {
	    	logger.error("Couldn't parse number of bytes in GET response");
	    	RunMW.shutdown();
	    }
    	
    	buffer.position(buffer.position() + datablockSize + 2);
	    return newlineFound;
	}
}

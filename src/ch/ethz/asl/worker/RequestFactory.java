package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.List;

import ch.ethz.asl.RunMW;

public class RequestFactory {

	/***
	 * Maximum number of keys that can be requested in a single 'get' request.
	 */
	static final int MAX_MULTIGETS_SIZE = 10;
	
	/***
	 * Parses commands from the client and creates Request objects
	 * 
	 * @param readBuffer
	 *            Contains the command to parse. Its position must be set to the
	 *            last byte of the read request
	 * @return A Request object with the parsed request
	 * @throws FaultyRequestException
	 *             If the command is unsupported or invalid and there's no way to recover
	 * @throws IncompleteRequestException
	 * 			   If the command is invalid, but possibly only incomplete
	 */
	public static Request tryParseClientRequest(ByteBuffer readBuffer) throws FaultyRequestException, IncompleteRequestException {

		Request req = null;
		int oldPosition = readBuffer.position();
		int messageLength = oldPosition;
		readBuffer.flip();
		
		
		if(messageLength < 4)
			// the shortest request is 'get a\r\n'
			throw new IncompleteRequestException("Request too short");
		
		if(startsWithGet(readBuffer)) {
			
			readBuffer.position(4);
		    byte whitespaceCount = 0;
		    boolean newlineFound = false;
		    while(!newlineFound && readBuffer.hasRemaining()) {
		    	
		    	char currentChar = (char)readBuffer.get();
		    	char nextChar = (char)readBuffer.get();
			    if(currentChar == '\r' && nextChar == '\n') {
				    newlineFound = true;
		            break;
			    }
			    else {
			    	readBuffer.position(readBuffer.position() - 1);
		            if(currentChar == ' ') {
		                whitespaceCount++;
		                if(whitespaceCount > 9)
		                	throw new FaultyRequestException(
									String.format("Encountered more than %d keys in a get request", MAX_MULTIGETS_SIZE));
		            }
			    }
		    }
			
		    if(!newlineFound) {
		    	throw new IncompleteRequestException("Encountered incomplete get request");
		    }
		    else if(readBuffer.hasRemaining()) { //if(newlineFound
		    	throw new FaultyRequestException("Encountered more text after get request and newline");
		    } else {
		    	if(whitespaceCount == 0) {
		    		req = new GetRequest(readBuffer);
		    	}
		    	else {
		    		if(RunMW.readSharded)
						req = new ShardedMultiGetRequest(readBuffer, whitespaceCount + 1);
					else
						req = new NonShardedMultiGetRequest(readBuffer, whitespaceCount + 1);
		    	}
		    }
			
		}
		else if(startsWithSet(readBuffer)) {
			
			readBuffer.position(4);
		    int whitespaceCount = 0;
		    int firstNewlinePos = -1;
		    boolean newlineFound = false;
		    int numBytesPos = -1;
		    while(!newlineFound && readBuffer.hasRemaining()) {
		    	
		    	char currentChar = (char)readBuffer.get();
		    	char nextChar = (char)readBuffer.get();
			    if(currentChar == '\r' && nextChar == '\n') {
				    newlineFound = true;
		            firstNewlinePos = readBuffer.position() - 2;
			    }
			    else {
			    	readBuffer.position(readBuffer.position() - 1);
		            if(currentChar == ' ') {
		                whitespaceCount++;
		                if(whitespaceCount == 3)
			            	numBytesPos = readBuffer.position();
		                else if(whitespaceCount > 3)
		                	throw new FaultyRequestException("First line of set request contained more than 5 tokens.");
		            }
		            
			    }
		    }
			
		    if(!readBuffer.hasRemaining()) {
		    	throw new IncompleteRequestException("Encountered incomplete set request");
		    }
		    else { //if(newlineFound && hasRemaining
		    	
		    	// Parse size of data block
		    	String numBytesText = new String(readBuffer.array(), numBytesPos, readBuffer.position() - numBytesPos - 2);
		    	int datablockSize;
		    	try {
		    		datablockSize = Integer.parseInt(numBytesText);
			    } catch(NumberFormatException ex) {
					throw new FaultyRequestException("Encountered set request without valid size of the datablock");
				}
		    	if(datablockSize > Request.MAX_DATABLOCK_SIZE) {
					throw new FaultyRequestException(String.format("Encountered set request with a too large datablock of %d bytes", Request.MAX_DATABLOCK_SIZE));
				}
		    	
		    	
		    	int nextNewlinePos = firstNewlinePos + datablockSize + 2;
		    	if(!nextNewlinePosIsCorrect(nextNewlinePos, readBuffer)) {
		    		throw new FaultyRequestException("Number of bytes in set request doesn't match real datablock size");
		    	}
		    	else {
		    		readBuffer.position(nextNewlinePos + 2);
		    		if(readBuffer.hasRemaining()) {
		    			throw new FaultyRequestException("Read more characters after an already valid request");
		    		} else {
		    			req = new SetRequest(readBuffer);
		    		}
		    	}
		    }
		}
		else {
			throw new FaultyRequestException("Encountered neither get, nor set request.");
		}
		
		
		readBuffer.flip();
		return req;
	}

	private static boolean nextNewlinePosIsCorrect(int nextNewlinePos, ByteBuffer readBuffer) {
		return readBuffer.limit() >= nextNewlinePos + 2 && (char)readBuffer.get(nextNewlinePos) == '\r' && (char)readBuffer.get(nextNewlinePos + 1) == '\n';  
	}

	private static boolean startsWithGet(ByteBuffer readBuffer) {
		return (char)readBuffer.get(0) == 'g' && (char)readBuffer.get(1) == 'e' && (char)readBuffer.get(2) == 't' && (char)readBuffer.get(3) == ' ';
	}
	
	private static boolean startsWithSet(ByteBuffer readBuffer) {
		return (char)readBuffer.get(0) == 's' && (char)readBuffer.get(1) == 'e' && (char)readBuffer.get(2) == 't' && (char)readBuffer.get(3) == ' ';
	}

	public static ByteBuffer constructMultiGetRequest(List<String> assignedKeys, ByteBuffer buffer) {
		StringBuilder bld = new StringBuilder("get");
		for(String key : assignedKeys) {
			bld.append(" ");
			bld.append(key);
		}
		bld.append("\r\n");
		buffer.put(bld.toString().getBytes());
		buffer.flip();
		return buffer;
	}
}

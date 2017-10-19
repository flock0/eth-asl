package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
		readBuffer.position(0);
		
		
		if(messageLength < 7)
			// the shortest request is 'get a\r\n'
			throw new IncompleteRequestException("Requests can't be shorter than 7 bytes");
		
		byte[] commandStartArr = new byte[3];
		readBuffer.get(commandStartArr);
		
		String commandStart = new String(commandStartArr);
		if(startsWithGet(readBuffer)) {
			// Get the whole request so far
			readBuffer.position(0);
			byte[] commandArr = new byte[messageLength];
			readBuffer.get(commandArr);
			
			String command = new String(commandArr);
			
			// Split up commands along \r\n to find the first line.
			String[] newlineSplit = command.split("\r\n");

			// Split lines along whitespaces to disect the commands.
			String[] whitespaceSplit = newlineSplit[0].split(" ");
			
			if (newlineSplit.length > 1)
				throw new FaultyRequestException("Multiple lines not allowed in 'get' command.");
			
			if(command.endsWith("\r\n")) {
				if(whitespaceSplit.length < 2) {
					throw new FaultyRequestException("No key found in 'get' command.");
				}
				else if(whitespaceSplit.length > MAX_MULTIGETS_SIZE + 1) {
					throw new FaultyRequestException(
							String.format("Encountered more than %d requested keys in a get request", MAX_MULTIGETS_SIZE));
				}
				else if(whitespaceSplit.length == 2){
					// We encountered a simple get command
					String key = whitespaceSplit[1];
					req = new GetRequest(readBuffer, key);
				}
				else {
					// We encountered a multiget request
					List<String> keys = new ArrayList<String>();
					for (int i = 1; i < whitespaceSplit.length; i++)
						keys.add(whitespaceSplit[i]);

					if(RunMW.readSharded)
						req = new ShardedMultiGetRequest(readBuffer, keys);
					else
						req = new NonShardedMultiGetRequest(readBuffer, keys);
				}
			}
			else { // command doesn't end with \r\n
				throw new IncompleteRequestException("Found possible incomplete get-request.");
			}
		}
		else if(startsWithSet(readBuffer)) {
			// Get the whole request so far
			readBuffer.position(0);
			byte[] commandArr = new byte[messageLength];
			readBuffer.get(commandArr);
			
			String command = new String(commandArr);
			
			if(command.endsWith("\r\n")) {
				int firstNewlinePos = command.indexOf("\r\n");
				if (firstNewlinePos == command.length() - 2) { // Is there only one \r\n?
					throw new IncompleteRequestException("Encountered a set request with only one newline. There should be two lines.");
				}
				else {
					String[] whitespaceSplit = command.substring(0, firstNewlinePos).split(" ");
					if(whitespaceSplit.length != 5) { // A valid set-request should look like: 'set <key> <flags> <expires> <bytes>\r\n...'
						throw new FaultyRequestException("First line of set request contained more than 5 tokens.");
					}
					else {
						int datablockSize;
						try {
							datablockSize = Integer.parseInt(whitespaceSplit[4]);
						} catch(NumberFormatException ex) {
							throw new FaultyRequestException("Encountered set request without valid size of the datablock");
						}
						if(datablockSize > Request.MAX_DATABLOCK_SIZE) {
							throw new FaultyRequestException(String.format("Encountered set request with a too large datablock of %d bytes", Request.MAX_DATABLOCK_SIZE));
							
						}
						else {
							int nextNewlinePos = firstNewlinePos + 2 + datablockSize;
							if(nextNewlinePos != commandArr.length - 2) {
								throw new FaultyRequestException("Number of bytes in set request doesn't match real datablock size");
							} else {
								req = new SetRequest(readBuffer);
							}
							
						}
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

	private static boolean startsWithGet(ByteBuffer readBuffer) {
		return (char)readBuffer.get(0) == 'g' && (char)readBuffer.get(1) == 'e' && (char)readBuffer.get(2) == 't';
	}
	
	private static boolean startsWithSet(ByteBuffer readBuffer) {
		return (char)readBuffer.get(0) == 's' && (char)readBuffer.get(1) == 'e' && (char)readBuffer.get(2) == 't';
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

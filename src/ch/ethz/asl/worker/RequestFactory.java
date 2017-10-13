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
	 * @param buffer
	 *            Contains the command to parse. Its position must be set to the
	 *            last byte of the read request
	 * @return A Request object with the parsed request
	 * @throws FaultyRequestException
	 *             If the command is unsupported or invalid and there's no way to recover
	 * @throws IncompleteRequestException
	 * 			   If the command is invalid, but possibly only incomplete
	 */
	public static Request tryParseClientRequest(ByteBuffer buffer) throws FaultyRequestException, IncompleteRequestException {

		Request req = null;
		int oldPosition = buffer.position();
		int messageLength = oldPosition;
		buffer.position(0);
		
		
		if(messageLength < 7)
			// the shortest request is 'get a\r\n'
			throw new IncompleteRequestException("Requests can't be shorter than 7 bytes");
		
		byte[] commandStartArr = new byte[3];
		buffer.get(commandStartArr);
		
		String commandStart = new String(commandStartArr);
		if(commandStart.equals("get")) {
			// Get the whole request so far
			buffer.position(0);
			byte[] commandArr = new byte[messageLength];
			buffer.get(commandArr);
			
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
					req = new GetRequest(buffer, key);
				}
				else {
					// We encountered a multiget request
					List<String> keys = new ArrayList<String>();
					for (int i = 1; i < whitespaceSplit.length; i++)
						keys.add(whitespaceSplit[i]);

					if(RunMW.readSharded)
						throw new UnsupportedOperationException("Not yet implemented");
					else
						req = new NonShardedMultiGetRequest(buffer, keys);
				}
			}
			else { // command doesn't end with \r\n
				throw new IncompleteRequestException("Found possible incomplete get-request.");
			}
		}
		else if(commandStart.equals("set")) {
			// Get the whole request so far
			buffer.position(0);
			byte[] commandArr = new byte[messageLength];
			buffer.get(commandArr);
			
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
								req = new SetRequest(buffer);
							}
							
						}
					}
				}
			}
		}
		else {
			throw new FaultyRequestException("Encountered neither get, nor set request.");
		}
		
		
		buffer.flip();
		return req;
	}
}

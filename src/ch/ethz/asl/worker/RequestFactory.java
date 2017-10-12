package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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
	public static Request tryCreateRequest(ByteBuffer buffer) throws FaultyRequestException, IncompleteRequestException {

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
			
			String command = new String(command);
			
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
					return new GetRequest(command, key);
				}
				else {
					// We encountered a multiget request
					List<String> keys = new ArrayList<String>();
					for (int i = 1; i < whitespaceSplit.length; i++)
						keys.add(whitespaceSplit[i]);

					return new MultiGetRequest(command, keys);
				}
			}
			else { // command doesn't end with \r\n
				if(whitespaceSplit.length > MAX_MULTIGETS_SIZE + 1) {
					throw new FaultyRequestException(
							String.format("Encountered more than %d requested keys in a get request", MAX_MULTIGETS_SIZE));
				}
				else {
					throw new IncompleteRequestException("Found possible incomplete get-request.");
				}
			}
		}
		else if(commandStart.equals("set")) {
			// Get the whole request so far
			buffer.position(0);
			byte[] commandArr = new byte[messageLength];
			buffer.get(commandArr);
			
			String command = new String(command);
		}
		else {
			throw new FaultyRequestException("Encountered neither get, nor set request.");
		}
		
		//TODO proper setting of buffer position, depending on return value or exception.
		buffer.position(messageLength);
		return isValid;
	}
}

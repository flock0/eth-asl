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
	 * @throws RequestParsingException
	 *             If the command is unsupported or invalid
	 */
	public static Request createRequest(ByteBuffer buffer) throws RequestParsingException {

		int messageLength = buffer.position();
		buffer.position(0);
		byte[] arr = buffer.array();
		String command = new String(arr).substring(0, messageLength);
		// Split up commands along \r\n to find the first line.
		String[] newlineSplit = command.split("\r\n");

		// Split lines along whitespaces to disect the commands.
		String[] whitespaceSplit = newlineSplit[0].split(" ");

		if (whitespaceSplit[0].equals("get")) {
			// A get command should not span multiple lines. (compare with set commands)
			if (newlineSplit.length > 1)
				throw new RequestParsingException("Multiple lines not allowed in 'get' command.");

			// Have we encountered an incomplete get command
			if (whitespaceSplit.length < 2) {
				throw new RequestParsingException("No key found in 'get' command.");

			} else if (whitespaceSplit.length == 2) {
				// We encountered a simple get command
				String key = whitespaceSplit[1];
				return new GetRequest(command, key);

			} else if (whitespaceSplit.length <= MAX_MULTIGETS_SIZE) {
				// We encountered a multiget request
				List<String> keys = new ArrayList<String>();
				for (int i = 1; i < whitespaceSplit.length; i++)
					keys.add(whitespaceSplit[i]);

				return new MultiGetRequest(command, keys);

			} else {
				// Too many requested keys in a multiget
				throw new RequestParsingException(
						String.format("Encountered more than %d requested keys in a get request", MAX_MULTIGETS_SIZE));
			}

		} else if (whitespaceSplit[0].equals("set")) {
			return null; // TODO
		} else {
			throw new RequestParsingException(String.format("'%s' command is unknown.", whitespaceSplit[0]));
		}
	}
}

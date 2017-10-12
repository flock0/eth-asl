package ch.ethz.asl.worker;

/***
 * Indicates that an unknown command has been tried to parse,
 * but the command might only be incomplete.
 * @author Florian Chlan
 *
 */
public class IncompleteRequestException extends Exception {
	
	public IncompleteRequestException(String message) {
		super(message);
	}
}

package ch.ethz.asl.worker;

/***
 * Indicates that an unknown command has been tried to parse.
 * @author Florian Chlan
 *
 */
public class RequestParsingException extends Exception {

	private static final long serialVersionUID = 1693345615314533055L;
	
	public RequestParsingException(String message) {
		super(message);
	}
}

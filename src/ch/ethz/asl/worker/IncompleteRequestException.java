package ch.ethz.asl.worker;

/***
 * Indicates that an unknown command has been tried to parse,
 * but the command might only be incomplete.
 * @author Florian Chlan
 *
 */
public class IncompleteRequestException extends Exception {

	private static final long serialVersionUID = -2580321434258375168L;

	public IncompleteRequestException(String message) {
		super(message);
	}
}

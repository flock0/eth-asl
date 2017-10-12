package ch.ethz.asl.worker;

/***
 * Indicates that an unknown command has been tried to parse
 * and there's no way to recover.
 * @author Florian Chlan
 *
 */
public class FaultyRequestException extends Exception {

	private static final long serialVersionUID = 1693345615314533055L;
	
	public FaultyRequestException(String message) {
		super(message);
	}
}

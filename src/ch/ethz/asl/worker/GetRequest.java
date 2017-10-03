package ch.ethz.asl.worker;

public class GetRequest implements Request {

	String command;
	String key;
	public GetRequest(String command, String key) {
		this.command = command;
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public Object getCommand() {
		return command;
	}

}

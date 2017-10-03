package ch.ethz.asl.worker;

import java.util.List;

public class MultiGetRequest implements Request {

	String command;
	List<String> keys;
	public MultiGetRequest(String command, List<String> keys) {
		this.command = command;
		this.keys = keys;
	}

	@Override
	public Object getCommand() {
		return command;
	}
	
	public List<String> getKeys() {
		return keys;
	}

	

}

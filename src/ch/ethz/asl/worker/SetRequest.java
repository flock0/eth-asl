package ch.ethz.asl.worker;

public class SetRequest implements Request {

	byte[] command;
	public SetRequest(byte[] command) {
		this.command = command;
	}

	@Override
	public byte[] getCommand() {
		return command;
	}

}

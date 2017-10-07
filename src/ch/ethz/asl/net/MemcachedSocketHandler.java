package ch.ethz.asl.net;

import java.util.List;

public class MemcachedSocketHandler {
	private static List<String> mcAddresses;
	
	public static void setMcAddresses(List<String> mcAddresses) {
		MemcachedSocketHandler.mcAddresses = mcAddresses;
	}
	
	public MemcachedSocketHandler() {
		
	}
}

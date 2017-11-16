package ch.ethz.asl.worker;

public class HashingLoadBalancer {
	private static int numServers;
	
	public static void setNumServers(int numServers) {
		HashingLoadBalancer.numServers = numServers;
	}
	public static int findTargetServer(String key) {
		return Math.floorMod(key.hashCode(), numServers);  
	}
}

package ch.ethz.asl.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HashingLoadBalancer {
	private static int numServers;
	
	public static void setNumServers(int numServers) {
		HashingLoadBalancer.numServers = numServers;
	}
	
	public static int findTargetServer(String key) {
		return Math.floorMod(key.hashCode(), numServers);  
	}
	
	public static Pair<HashMap<Integer, List<String>>, List<Integer>> assignKeysToServers(String[] keys, int startingServerIndex) {
		List<Integer> answerAssemblyOrder = new ArrayList<Integer>();
		HashMap<Integer, List<String>> keySplits = new HashMap<>();
		int maxKeysPerServer = (int) Math.ceil((double)keys.length / numServers);
		
		int currentServer = startingServerIndex;
		int keyPerServerCount = 0;
		for(String key : keys) {
			if(!keySplits.containsKey(currentServer)) {
				answerAssemblyOrder.add(currentServer);
				keySplits.put(currentServer, new ArrayList<>());
			}
			keySplits.get(currentServer).add(key);
			
			if(++keyPerServerCount == maxKeysPerServer) {
				currentServer = (currentServer + 1) % numServers;
				keyPerServerCount = 0;
			}
		}
		
		return new Pair<HashMap<Integer, List<String>>, List<Integer>>(keySplits, answerAssemblyOrder);
	}
}

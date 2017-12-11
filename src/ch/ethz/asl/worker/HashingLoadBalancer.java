package ch.ethz.asl.worker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Contains methods to find the designated server of GET requests.
 * Also handles splitting up sharded requests among the available servers.
 * @author Florian Chlan
 *
 */
public class HashingLoadBalancer {
	private static int numServers;
	
	public static void setNumServers(int numServers) {
		HashingLoadBalancer.numServers = numServers;
	}
	
	/**
	 * Finds the designated server by hashing the keys.
	 * For single GETs this is the single key.
	 * For MultiGETs this is the string with all the keys (separated by whitespaces).
	 */
	public static int findTargetServer(String key) {
		return Math.floorMod(key.hashCode(), numServers);  
	}
	
	/**
	 * Fairly assigns a MultiGETs keys to the servers, starting with the provided primary server. 
	 * @return A Map of (serverIndex -> keys for that server).
	 *         A list of the same length as the number of keys requested. Each element in the list indicates which server that key should be sent to.
	 */
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

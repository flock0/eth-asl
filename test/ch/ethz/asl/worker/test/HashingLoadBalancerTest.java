package ch.ethz.asl.worker.test;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.ethz.asl.worker.HashingLoadBalancer;
import ch.ethz.asl.worker.Pair;

public class HashingLoadBalancerTest {

	Random rnd;
	final int numIterations = 1000000;
	@Before
	public void setUp() throws Exception {
		rnd = new Random();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSingleKeysWithSuffix() {
		System.out.println("testSingleKeysWithSuffix");
		HashingLoadBalancer.setNumServers(3);

		HashMap<Integer, Integer> hits = new HashMap<>();
		for(int i = 0; i < numIterations; i++) {
			String key = createTestKey("memtier-", 10000);
			int targetServer = HashingLoadBalancer.findTargetServer(key);
			if(hits.containsKey(targetServer))
				hits.put(targetServer, hits.get(targetServer) + 1);
			else
				hits.put(targetServer, 1);
		}
		
		for(Integer server : hits.keySet()) {
			System.out.println(String.format("Server %d was hit %d times", server, hits.get(server)));
		}
	}

	@Test
	public void testSingleKeysWithRandomStrings() {
		System.out.println("testSingleKeysWithRandomStrings");
		HashingLoadBalancer.setNumServers(3);

		HashMap<Integer, Integer> hits = new HashMap<>();
		for(int i = 0; i < numIterations; i++) {
			String key = createTestKey("memtier-", 10000);
			int targetServer = HashingLoadBalancer.findTargetServer(key);
			if(hits.containsKey(targetServer))
				hits.put(targetServer, hits.get(targetServer) + 1);
			else
				hits.put(targetServer, 1);
		}
		
		for(Integer server : hits.keySet()) {
			System.out.println(String.format("Server %d was hit %d times", server, hits.get(server)));
		}
	}
	
	@Test
	public void testMultiGetsWithSuffix() {
		System.out.println("testMultiGetsWithSuffix");
		HashingLoadBalancer.setNumServers(3);

		HashMap<Integer, Integer> hits = new HashMap<>();
		for(int i = 0; i < numIterations; i++) {
			String key = createTestMultiKey("memtier-", 10000, 10);
			int targetServer = HashingLoadBalancer.findTargetServer(key);
			if(hits.containsKey(targetServer))
				hits.put(targetServer, hits.get(targetServer) + 1);
			else
				hits.put(targetServer, 1);
		}
		
		for(Integer server : hits.keySet()) {
			System.out.println(String.format("Server %d was hit %d times", server, hits.get(server)));
		}
	}
	
	@Test
	public void testShardedServerAssignmentWithSuffix() {
		System.out.println("testShardedServerAssignmentWithSuffix");
		HashingLoadBalancer.setNumServers(3);

		HashMap<Integer, Integer> hits = new HashMap<>();
		for(int i = 0; i < numIterations; i++) {
			String key = createTestMultiKey("memtier-", 10000, 10);
			int primaryServer = HashingLoadBalancer.findTargetServer(key);
			Pair<HashMap<Integer, List<String>>, List<Integer>> pair = HashingLoadBalancer.assignKeysToServers(key.split(" "), primaryServer);
			
			HashMap<Integer, List<String>> keySplits = pair.left;
			for(Integer server : keySplits.keySet()) {
				if(hits.containsKey(server))
					hits.put(server, hits.get(server) + keySplits.get(server).size());
				else
					hits.put(server, keySplits.get(server).size());
			}
			
		}
		
		for(Integer server : hits.keySet()) {
			System.out.println(String.format("Server %d was hit %d times", server, hits.get(server)));
		}
	}

	private String createTestMultiKey(String prefix, int maxKey, int maxNumMultiKeys) {
		int desiredMultiGetSize = 1 + rnd.nextInt(maxNumMultiKeys);
		StringBuilder sb = new StringBuilder();
		
		sb.append(createTestKey(prefix, maxKey));
		for(int i = 0; i < desiredMultiGetSize; i++) {
			sb.append(' ');
			sb.append(createTestKey(prefix, maxKey));
		}
		return sb.toString();
	}

	private String createTestKey(String prefix, int maxKey) {
		return String.format("%s%d", prefix, rnd.nextInt(maxKey));
	}
	

}

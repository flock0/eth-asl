package ch.ethz.asl.net;

import java.nio.channels.SelectionKey;
import java.util.HashMap;

public class ReceivalTimers {

	private HashMap<SelectionKey, Integer> numReadsMap = new HashMap<>();
	private HashMap<SelectionKey, Long> firstReadsTime = new HashMap<>();
	public void readingFor(SelectionKey key) {
		if (numReadsMap.containsKey(key)) {
			numReadsMap.put(key, numReadsMap.get(key) + 1);
		} else {
			numReadsMap.put(key, 1);
			firstReadsTime.put(key, new Long(System.nanoTime()));
		}
			
	}

	public Integer getNumReads(SelectionKey key) {
		return numReadsMap.get(key);
	}

	public Long getFirstReadTime(SelectionKey key) {
		return firstReadsTime.get(key);
	}

	public void reset(SelectionKey key) {
		numReadsMap.remove(key);
		firstReadsTime.remove(key);
		
	}

}

package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;

public abstract class Request {
	
	/***
	 * Maximum size of a request or answer is around 10200 bytes.
	 */
	public static final int MAX_REQUEST_SIZE = 3000;
	
	/***
	 * Maximum size  of datablocks in set commands is 1024 bytes.
	 */
	public static final int MAX_DATABLOCK_SIZE = 1024;
	
	private long initializeClockTime;
	private long initializeTime;
	private long enqueueTime;
	private long dequeueTime;
	private long beforeSendTime;
	private long afterReceiveTime;
	private long completedTime;
	private int queueLength;
	protected int numKeysRequested = 0;
	private int numHits = 0;
	
	protected Request() {
		initializeTime = System.nanoTime();
		initializeClockTime = System.currentTimeMillis();
	}
	
	public abstract byte[] getCommand();

	public abstract void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client);
	
	public void setEnqueueTime() {
		enqueueTime = System.nanoTime();
	}
	
	public void setDequeueTime() {
		dequeueTime = System.nanoTime();
	}

	public void setBeforeSendTime() {
		beforeSendTime = System.nanoTime();
	}
	
	public void setAfterReceiveTime() {
		afterReceiveTime = System.nanoTime();
	}
	
	public void setCompletedTime() {
		completedTime = System.nanoTime();
		
	}

	public void setQueueLength(int queueLength) {
		this.queueLength = queueLength; 
		
	}

	public void writeLog() {
		// TODO
	}

	public void setNumHits(int numHits) {
		this.numHits = numHits;
	}
}

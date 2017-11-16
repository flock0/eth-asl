package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public abstract class Request {
	
	private static final Logger requestLogger = LogManager.getLogger("request_logger");
	
	/***
	 * Maximum size of a request or answer is around 10200 bytes.
	 */
	public static final int MAX_REQUEST_SIZE = 3000;
	
	/***
	 * Maximum size  of datablocks in set commands is 1024 bytes.
	 */
	public static final int MAX_DATABLOCK_SIZE = 1024;
	
	private boolean shouldLog = true;
	private long initializeClockTime;
	private long arrivalTime;
	private long previousArrivalTime;
	private long initializeTime;
	private long enqueueTime;
	private long dequeueTime;
	private long beforeSendTime;
	private long afterReceiveTime;
	private long completedTime;
	private int queueLength;
	protected int numKeysRequested = 0;
	private int numHits = 0;
	private int requestSize;
	private int responseSize;

	

	protected Request() {
		initializeTime = System.nanoTime();
		initializeClockTime = System.currentTimeMillis();
	}
	
	public abstract byte[] getCommand();

	public abstract void handle(MemcachedSocketHandler memcachedSocketHandler, SocketChannel client);
	
	public abstract String getRequestType();
	public abstract int getFirstTargetServer();
	public abstract int getNumOfTargetServers();
	
	public void setEnqueueTime() {
		if(shouldLog)
			enqueueTime = System.nanoTime();
	}
	
	public void setDequeueTime() {
		if(shouldLog)
			dequeueTime = System.nanoTime();
	}

	public void setBeforeSendTime() {
		if(shouldLog)
			beforeSendTime = System.nanoTime();
	}
	
	public void setAfterReceiveTime() {
		if(shouldLog)
			afterReceiveTime = System.nanoTime();
	}
	
	public void setCompletedTime() {
		if(shouldLog)
			completedTime = System.nanoTime();
	}

	public void setQueueLength(int queueLength) {
		if(shouldLog)
			this.queueLength = queueLength; 
		
	}

	public void writeLog() {
		if(shouldLog)
			requestLogger.debug(String.format("%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d", getRequestType(), getFirstTargetServer(), getNumOfTargetServers(), initializeClockTime, previousArrivalTime, arrivalTime, initializeTime, enqueueTime, dequeueTime, beforeSendTime, afterReceiveTime, completedTime, queueLength, numKeysRequested, numHits, requestSize, responseSize));
	}

	public void setNumHits(int numHits) {
		if(shouldLog)
			this.numHits = numHits;
	}

	public void setRequestSize(int requestSize) {
		if(shouldLog)
			this.requestSize = requestSize;
	}

	public void setResponseSize(int responseSize) {
		if(shouldLog)
			this.responseSize = responseSize;
	}

	public void setArrivalTime(long arrivalTime) {
		if(shouldLog)
			this.arrivalTime = arrivalTime;
		
	}

	public void setPreviousArrivalTime(long previousArrivalTime) {
		if(shouldLog)
			this.previousArrivalTime = previousArrivalTime;
	}
	
	public void dontLog() {
		shouldLog = false;
	}
}

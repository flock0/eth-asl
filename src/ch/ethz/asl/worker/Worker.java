package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ThreadLocalRandom;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

public class Worker implements Runnable {
	
	private static final Integer LOG_INTERVAL = 10; // Take a sample by only storing every 20th request on average   
    private SocketChannel client;
	private Request request;
	
    private static final ThreadLocal<MemcachedSocketHandler> sockets = 
           new ThreadLocal<MemcachedSocketHandler>(){
        @Override
        protected MemcachedSocketHandler initialValue(){
            return new MemcachedSocketHandler();
        }
        
        
    };        	
	
    public Worker(SocketChannel client, Request req){              
        this.client = client;
        this.request = req;
    }

    public void run(){
    	if(ThreadLocalRandom.current().nextInt(LOG_INTERVAL) != 0)
    		request.dontLog();
    	request.setDequeueTime();
    	RunMW.setQueueLength(request);
    	request.handle(sockets.get(), client);
    	request.setCompletedTime();
    	request.writeLog();
    }
}
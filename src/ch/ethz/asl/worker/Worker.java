package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;

import ch.ethz.asl.RunMW;
import ch.ethz.asl.net.MemcachedSocketHandler;

public class Worker implements Runnable {
	
	private static final Integer LOG_INTERVAL = 100; // Take a sample by only storing every 100th request    
    private SocketChannel client;
	private Request request;
	
    private static final ThreadLocal<MemcachedSocketHandler> sockets = 
           new ThreadLocal<MemcachedSocketHandler>(){
        @Override
        protected MemcachedSocketHandler initialValue(){
            return new MemcachedSocketHandler();
        }
        
        
    };        	

    private static final ThreadLocal<Integer> logCounter = 
            new ThreadLocal<Integer>(){
         @Override
         protected Integer initialValue(){
             return new Integer(0);
         }
         
         
     };
	
    public Worker(SocketChannel client, Request req){              
        this.client = client;
        this.request = req;
    }

    public void run(){
    	//ThreadContext.put("KEY", Thread.currentThread().getName());
    	request.setDequeueTime();
    	RunMW.setQueueLength(request);
    	request.handle(sockets.get(), client);
    	request.setCompletedTime();
    	Integer counter = logCounter.get();
    	if(counter % LOG_INTERVAL == 0) {
    		request.writeLog();
    		counter = 0;
    	}
    	counter += 1;
    }
}
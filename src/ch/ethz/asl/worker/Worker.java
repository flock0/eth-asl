package ch.ethz.asl.worker;

import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class Worker implements Runnable {
	
    private SocketChannel client;
	private Request request;
    
	private static final Logger logger = LogManager.getLogger(Worker.class);
	
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
    	logger.debug(String.format("Processing %s on worker thread", request));
    	request.handle(sockets.get(), client);
    }
}
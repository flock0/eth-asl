package ch.ethz.asl.worker;

import java.nio.channels.SelectionKey;

import ch.ethz.asl.net.MemcachedSocketHandler;

public class Worker implements Runnable {
    private SelectionKey key;
    private static final ThreadLocal<MemcachedSocketHandler> sockets = 
           new ThreadLocal<MemcachedSocketHandler>(){
        @Override
        protected MemcachedSocketHandler initialValue(){
            return new MemcachedSocketHandler();
        }
        
        
    };

    public Worker(SelectionKey key){              
        this.key = key;
    }

    public void run(){      
    	MemcachedSocketHandler s = sockets.get(); //gets from threadlocal
        //send data on socket based on workDetails, etc.
    }
}
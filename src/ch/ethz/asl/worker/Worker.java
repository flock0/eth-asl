package ch.ethz.asl.worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ch.ethz.asl.net.MemcachedSocketHandler;
import ch.ethz.asl.net.SocketsHandler;

public class Worker implements Runnable {
	
	static final int CLIENT_BUFFER_MAX_BYTES_SIZE = 3000; //Maximum size of a get command is 2513
    private SelectionKey key;
	private SocketsHandler socketsHandler;
    
	private static final Logger logger = LogManager.getLogger(Worker.class);
	
    private static final ThreadLocal<MemcachedSocketHandler> sockets = 
           new ThreadLocal<MemcachedSocketHandler>(){
        @Override
        protected MemcachedSocketHandler initialValue(){
            return new MemcachedSocketHandler();
        }
        
        
    };
         
	private static final ThreadLocal<ByteBuffer> clientBuffer = 
	         new ThreadLocal<ByteBuffer>(){
	      @Override
	      protected ByteBuffer initialValue(){
	          return ByteBuffer.allocate(CLIENT_BUFFER_MAX_BYTES_SIZE);
	      }
	      
	      
	};

    public Worker(SelectionKey key, SocketsHandler socketsHandler){              
        this.key = key;
        this.socketsHandler = socketsHandler;
    }

    public void run(){
    	logger.debug("Processing request on worker thread");
    	ByteBuffer clientBuff = clientBuffer.get();
    	SocketChannel client = (SocketChannel)key.channel();
    	
    	int readReturnCode = -2; //Initially set to some unused code
    	do {
    		try {
				readReturnCode = client.read(clientBuff);
			} catch (IOException ex) {
				logger.catching(ex);
			}
    	} while(readReturnCode != 0 && readReturnCode != -1); //TODO Handle overflow of buffer
    	
		if(readReturnCode == -1)
			try {
				client.close();
			} catch (IOException ex) {
				logger.catching(ex);
			}
		else {
			clientBuff.flip();
			try {
				Request req = RequestFactory.createRequest(clientBuff);
				clientBuff.clear();
				req.handle(sockets.get(), client, clientBuff);
			} catch (RequestParsingException ex) {
				//Couldn't parse 
				logger.catching(ex);
			} finally {
				clientBuff.clear();
				key.interestOps(SelectionKey.OP_READ);
				socketsHandler.wakeupSelector();
			}
		}
    }
}
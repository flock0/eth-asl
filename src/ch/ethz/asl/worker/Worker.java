package ch.ethz.asl.worker;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import ch.ethz.asl.net.MemcachedSocketHandler;
import ch.ethz.asl.net.SocketsHandler;

public class Worker implements Runnable {
	
	static final int CLIENT_BUFFER_MAX_BYTES_SIZE = 3000; //Maximum size of a get command is 2513
    private SelectionKey key;
	private SocketsHandler socketsHandler;
    
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
    	ByteBuffer clientBuff = clientBuffer.get();
    	SocketChannel client = (SocketChannel)key.channel();
    	
    	int readReturnCode = -2; //Initially set to some unused code
    	do {
    		readReturnCode = client.read(clientBuff);
    	} while(readReturnCode != 0 || readReturnCode != -1); //TODO Handle overflow of buffer
		
		if(readReturnCode == -1)
			//Client closed the connection. We skip this request.
			client.close();
		else {
			clientBuff.flip();
			Request req = RequestFactory.createRequest(clientBuff);
			req.handle(sockets.get(), clientBuff);
			clientBuff.clear();
			key.interestOps(SelectionKey.OP_READ);
			socketsHandler.wakeupSelector();
		}
		/*
			String result = new String(buffer.array()).trim();
			logger.debug(result);	
			writeBuffer.clear();
			int count = client.write(writeBuffer);
			logger.debug(count);
			key.interestOps(SelectionKey.OP_READ);
			sockHandler.getSelector().wakeup();
		*/
    }
}
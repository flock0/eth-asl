package ch.ethz.asl.test;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.ethz.asl.net.SocketsHandler;

public class SocketsHandlerTest {

	Thread thr;
	SocketsHandler handler;
	@Before
	public void setUp() throws Exception {
		thr = null;
		handler = null;
	}

	@After
	public void tearDown() throws Exception {
		if(thr != null && handler != null) {
			if(thr.isAlive()) {
				handler.shutdown();
				thr.join(2000);
				if(thr.isAlive())
					fail("SocketHandler thread could not be shutdown.");
			}
		}
	}

	@Test
	public void testRun() {
		fail("Not yet implemented");
		handler = new SocketsHandler("127.0.0.1", 42171);
		thr = new Thread(handler);
		thr.start();
		// TODO check if isrunning
		// TODO Try connecting to the socket and send some data
	}

	@Test
	public void testCloseServerSocket() {
		fail("Not yet implemented");
		// create and start thread
		//try connecting to socket
		//closeServerSocket
		//is still running
		// connecting to socket should fail
	}
	
	@Test
	public void testShutdown() {
		fail("Not yet implemented");
		// create and start thread
		//try connecting to socket
		//shutdown
		//isnotrunning
		// connecting to socket should fail
	}
	
	@Test
	public void testQueueEmpty() {
		fail("Not yet implemented");
		// create and start thread
		// queue should be empty
	}
	
	@Test
	public void testQueuedCorrectly() {
		fail("Not yet implemented");
		// create and start thread
		// connect and write to socket
		// queue should contain element
		// connect again and write to socket
		// queue should contain two elements
	}
}

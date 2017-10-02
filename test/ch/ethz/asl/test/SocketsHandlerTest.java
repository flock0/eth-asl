package ch.ethz.asl.test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import ch.ethz.asl.net.SocketsHandler;

public class SocketsHandlerTest {

	Thread thr;
	SocketsHandler handler;
	Socket sock1 = null;
	Socket sock2 = null;
	
	@Rule
    public ExpectedException thrown= ExpectedException.none();
	
	@Before
	public void setUp() throws Exception {
		thr = null;
		handler = null;
		Socket sock1 = null;
		Socket sock2 = null;
	}

	@After
	public void tearDown() throws Exception {
		if(sock1 != null)
			sock1.close();
		if(sock2 != null)
			sock2.close();
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
	public void testRun() throws UnknownHostException, IOException, InterruptedException {
		handler = new SocketsHandler("127.0.0.1", 42171);
		thr = new Thread(handler);
		thr.start();
		Thread.sleep(100);
		assertTrue(handler.isRunning());
		
		sock1 = new Socket("127.0.0.1", 42171);
		PrintWriter out1 = new PrintWriter(sock1.getOutputStream(), true);
		out1.println("GET 123456");
	}
	
	@Test
	public void testRunOnNetworkInterface() throws UnknownHostException, IOException, InterruptedException {
		InetAddress IP=InetAddress.getLocalHost();
		System.out.println("IP of my system is := "+IP.getHostAddress());
		handler = new SocketsHandler(IP.getHostAddress(), 42171);
		thr = new Thread(handler);
		thr.start();
		Thread.sleep(50);
		assertTrue(handler.isRunning());
		
		sock1 = new Socket(IP.getHostAddress(), 42171);
		PrintWriter out1 = new PrintWriter(sock1.getOutputStream(), true);
		out1.println("GET 123456");
	}
	
	@Test
	public void testCloseServerSocket() throws UnknownHostException, IOException, InterruptedException {
		// create and start thread
		handler = new SocketsHandler("127.0.0.1", 45343);
		thr = new Thread(handler);
		thr.start();
		Thread.sleep(100);
		//try connecting to socket
		sock1 = new Socket("127.0.0.1", 45343);
		PrintWriter out1 = new PrintWriter(sock1.getOutputStream(), true);
		out1.println("Command");
		
		//closeServerSocket
		handler.closeServerSocket();
		
		//is still running
		assertTrue(handler.isRunning());
		// connecting to socket should fail, but writing to existing sockets should succeed
		out1.println("Writing on already connected socket");
		//thrown.expect(ConnectException.class);
		try {
			sock2 = new Socket("127.0.0.1", 45343);
			fail("Expecte ConnectException");
		} catch(ConnectException ex) {
			
		}
		
	}
	
	@Test
	public void testShutdown() throws UnknownHostException, IOException, InterruptedException {
		// create and start thread
		handler = new SocketsHandler("127.0.0.1", 45343);
		thr = new Thread(handler);
		thr.start();
		Thread.sleep(100);
		//try connecting to socket
		sock1 = new Socket("127.0.0.1", 45343);
		PrintWriter out1 = new PrintWriter(sock1.getOutputStream(), true);
		out1.println("Command");
		
		//shutdown
		handler.shutdown();
		Thread.sleep(100);
		
		//isnotrunning
		assertFalse(handler.isRunning());
		
		// connecting to socket should fail
		thrown.expect(IOException.class);
		sock2 = new Socket("127.0.0.1", 45343);
	}
	
	@Test
	public void testQueueEmpty() {
		// create and start thread
		handler = new SocketsHandler("127.0.0.1", 32595);
		thr = new Thread(handler);
		thr.start();
		
		// queue should be empty
		assertEquals(0, handler.getChannelQueue().size());
	}
	
	@Test
	public void testQueuedCorrectly() throws UnknownHostException, IOException, InterruptedException {
		// create and start thread
		handler = new SocketsHandler("127.0.0.1", 45343);
		thr = new Thread(handler);
		thr.start();
		Thread.sleep(50);
		// connect and write to socket
		sock1 = new Socket("127.0.0.1", 45343);
		PrintWriter out1 = new PrintWriter(sock1.getOutputStream(), true);
		out1.println("GET 7484");
		
		// queue should contain element7
		assertEquals(1, handler.getChannelQueue().size());
		
		// connect again and write to socket
		sock2 = new Socket("127.0.0.1", 45343);
		PrintWriter out2 = new PrintWriter(sock2.getOutputStream(), true);
		out2.println("GET 9556");
		
		// queue should contain two elements
		assertEquals(2, handler.getChannelQueue().size());
	}
}

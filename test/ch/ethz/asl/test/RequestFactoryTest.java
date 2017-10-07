package ch.ethz.asl.test;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import ch.ethz.asl.worker.GetRequest;
import ch.ethz.asl.worker.MultiGetRequest;
import ch.ethz.asl.worker.Request;
import ch.ethz.asl.worker.RequestFactory;
import ch.ethz.asl.worker.RequestParsingException;
import ch.ethz.asl.worker.SetRequest;

public class RequestFactoryTest {

	
	@Rule
    public ExpectedException thrown = ExpectedException.none();
	
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSingleGet() throws RequestParsingException {
		String command = "get key-676544\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is GetRequest
		assertTrue(req instanceof GetRequest);
		GetRequest getReq = (GetRequest)req;
		assertEquals("key-676544", getReq.getKey());
		assertEquals(command, getReq.getCommand());
	}
	
	@Test
	public void testEmptyKeyGet() throws RequestParsingException {
		String command = "get \r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
	}
	
	@Test
	public void testNoKeyGet() throws RequestParsingException {
		String command = "get\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
	}
	
	@Test
	public void testNoNewline() throws RequestParsingException {
		String command = "get key234";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
	}
	
	@Test
	public void testMaxLengthKeyGet() throws RequestParsingException {
		String command = "get key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is GetRequest
		assertTrue(req instanceof GetRequest);
		GetRequest getReq = (GetRequest)req;
		assertEquals("key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890key4567890",
				getReq.getKey());
		assertEquals(command, getReq.getCommand());
	}
	
	@Test
	public void testUnknownCommand() throws RequestParsingException {
		String command = "BLA blub\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
	}
	
	@Test
	public void testThreeMultiGet() throws RequestParsingException {
		String command = "get blub blab blob\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is MultiGetRequest
		assertTrue(req instanceof MultiGetRequest);
		MultiGetRequest multiReq = (MultiGetRequest)req;
		assertEquals("blub", multiReq.getKeys().get(0));
		assertEquals("blab", multiReq.getKeys().get(1));
		assertEquals("blob", multiReq.getKeys().get(2));
		assertEquals(3, multiReq.getKeys().size());
		assertEquals(command, multiReq.getCommand());
		
	}
	
	@Test
	public void testMultiGetWithcommandkeys() throws RequestParsingException {
		String command = "get set get getset set1\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is MultiGetRequest
		assertTrue(req instanceof MultiGetRequest);
		MultiGetRequest multiReq = (MultiGetRequest)req;
		assertEquals("set", multiReq.getKeys().get(0));
		assertEquals("get", multiReq.getKeys().get(1));
		assertEquals("getset", multiReq.getKeys().get(2));
		assertEquals("set1", multiReq.getKeys().get(3));
		assertEquals(4, multiReq.getKeys().size());
		assertEquals(command, multiReq.getCommand());
	}
	
	@Test
	public void testTenMultiGet() throws RequestParsingException {
		String command = "get blub blab blob bleb blohb blarb blerb balorb burp bopp\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is MultiGetRequest
		assertTrue(req instanceof MultiGetRequest);
		MultiGetRequest multiReq = (MultiGetRequest)req;
		assertEquals("blub", multiReq.getKeys().get(0));
		assertEquals("blab", multiReq.getKeys().get(1));
		assertEquals("blob", multiReq.getKeys().get(2));
		assertEquals("bleb", multiReq.getKeys().get(3));
		assertEquals("blohb", multiReq.getKeys().get(4));
		assertEquals("blarb", multiReq.getKeys().get(5));
		assertEquals("blerb", multiReq.getKeys().get(6));
		assertEquals("balorb", multiReq.getKeys().get(7));
		assertEquals("burp", multiReq.getKeys().get(8));
		assertEquals("bopp", multiReq.getKeys().get(9));
		assertEquals(10, multiReq.getKeys().size());
		assertEquals(command, multiReq.getCommand());
		
	}
	
	@Test
	public void testElevenMultiGet() throws RequestParsingException {
		String command = "get blub blab blob bleb blohb blarb blerb balorb burp bopp baluhb\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
	}
	
	@Test
	public void testTwoCompleteCommands() throws RequestParsingException {
		String command = "get blub blab blob\r\nget moar\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
		
	}
	
	@Test
	public void testTwoIncompleteCommands() throws RequestParsingException {
		String command = "get blub blab blob\r\nget a";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		
		thrown.expect(RequestParsingException.class);
		RequestFactory.createRequest(buff);
		
	}
	
	@Test
	public void testSet() throws RequestParsingException {
		String command = "set key1234 0 1000 12\r\ndatadatadata\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is SetRequest
		assertTrue(req instanceof SetRequest);
		SetRequest setReq = (SetRequest)req;
		assertEquals(command, new String(setReq.getCommand()));
		
	}
	
	@Test
	public void testSetWithgetkeys() throws RequestParsingException {
		String command = "set get 0 1000 11\r\nget get get\r\n";
		ByteBuffer buff = ByteBuffer.allocate(3000);
		buff.put(command.getBytes());
		buff.flip();
		Request req = RequestFactory.createRequest(buff);
		
		//req is SetRequest
		assertTrue(req instanceof SetRequest);
		SetRequest setReq = (SetRequest)req;
		assertEquals(command, new String(setReq.getCommand()));
		
	}
	
}

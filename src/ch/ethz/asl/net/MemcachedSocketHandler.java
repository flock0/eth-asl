package ch.ethz.asl.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MemcachedSocketHandler {
	
	private static final Logger logger = LogManager.getLogger(MemcachedSocketHandler.class);
	
	private static List<String> mcAddresses;
	private List<SocketChannel> channels = new ArrayList<SocketChannel>();
	


	public static void setMcAddresses(List<String> mcAddresses) {
		MemcachedSocketHandler.mcAddresses = mcAddresses;
		logger.debug("Set new memcached addresses.");
	}
	
	public List<SocketChannel> getChannels() {
		return channels;
	}
	
	public MemcachedSocketHandler() {
		try {
			for(String addr : mcAddresses) {
				String[] splitAddr = addr.split(":");
				String host = splitAddr[0];
				int port = Integer.parseInt(splitAddr[1]);
				SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
				logger.debug(String.format("Connected to %s", addr));
				channels.add(channel);
			}
			logger.debug("Connected to all memcached servers.");
		} catch(IOException ex) {
			logger.catching(ex);
		}
	}
	
}

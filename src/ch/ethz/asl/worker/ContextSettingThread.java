package ch.ethz.asl.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class ContextSettingThread extends Thread {

	private static final Logger logger = LogManager.getLogger(ContextSettingThread.class);
	public ContextSettingThread(Runnable r) {
		super(r);
	}

	@Override
	public void run() {
		ThreadContext.put("KEY", Thread.currentThread().getName());
		logger.debug("Hit Thread context initialization");
		super.run();
	}

}

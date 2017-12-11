package ch.ethz.asl.worker;

import org.apache.logging.log4j.ThreadContext;

/**
 * A special thread implementation that stores the current thread name in the thread context.
 * This is used by log4j2 for creating different request log files for each thread. 
 * @author Florian Chlan
 *
 */
public class ContextSettingThread extends Thread {

	public ContextSettingThread(Runnable r) {
		super(r);
	}

	@Override
	public void run() {
		ThreadContext.put("KEY", Thread.currentThread().getName());
		super.run();
	}

}

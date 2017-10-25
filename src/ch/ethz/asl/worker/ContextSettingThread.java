package ch.ethz.asl.worker;

import org.apache.logging.log4j.ThreadContext;

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

package ch.ethz.asl.worker;

import java.util.concurrent.ThreadFactory;

public class ContextSettingThreadFactory implements ThreadFactory {

	@Override
	public Thread newThread(Runnable r) {
		return new ContextSettingThread(r);
	}

}

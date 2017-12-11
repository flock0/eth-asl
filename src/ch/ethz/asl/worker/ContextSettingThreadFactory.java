package ch.ethz.asl.worker;

import java.util.concurrent.ThreadFactory;

/**
 * A thread factory used with the thread pool to be able to log requests in a per-thread csv file.
 * @see ContextSettingThread
 * @author Florian Chlan
 *
 */
public class ContextSettingThreadFactory implements ThreadFactory {

	@Override
	public Thread newThread(Runnable r) {
		return new ContextSettingThread(r);
	}

}

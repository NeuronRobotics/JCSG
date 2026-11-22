package com.neuronrobotics.manifold3d;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Shared exclusion for native quality changes across bridge instances. */
final class NativeAccess {
	private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

	private NativeAccess() {
	}

	static Scope read() {
		return new Scope(LOCK.readLock());
	}

	static Scope write() {
		if (LOCK.getReadHoldCount() != 0 && !LOCK.isWriteLockedByCurrentThread())
			throw new IllegalStateException("Cannot change native precision during a native read operation");
		return new Scope(LOCK.writeLock());
	}

	static final class Scope implements AutoCloseable {
		private final Lock lock;

		private Scope(Lock lock) {
			this.lock = lock;
			lock.lock();
		}

		@Override
		public void close() {
			lock.unlock();
		}
	}
}

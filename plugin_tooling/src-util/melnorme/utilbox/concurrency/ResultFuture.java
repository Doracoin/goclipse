/*******************************************************************************
 * Copyright (c) 2016 Bruno Medeiros and other Contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Bruno Medeiros - initial API and implementation
 *******************************************************************************/
package melnorme.utilbox.concurrency;

import static melnorme.utilbox.core.Assert.AssertNamespace.assertFail;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import melnorme.utilbox.core.fntypes.Result;

/**
 * A future meant to be completed by an explicit {@link #setResult()} call. 
 * Similar to {@link CompletableFuture} but with a safer and simplified API, particularly:
 * 
 * - Uses the {@link FutureX} interface which has a safer and more precise API than {@link Future} 
 * with regards to exception throwing.
 * - By default, completing the Future ({@link #setResult()}) can only be attempted once, 
 * it is illegal for multiple {@link #setResult()} calls to be attempted.
 *
 */
public class ResultFuture<DATA, EXC extends Throwable> extends AbstractFutureX<DATA, EXC> {
	
	protected final CountDownLatch completionLatch = new CountDownLatch(1);
	protected final Object lock = new Object();
	
    protected volatile ResultStatus status = ResultStatus.INITIAL;
	protected volatile Result<DATA, EXC> result;
	
	public enum ResultStatus { INITIAL, RESULT_SET, CANCELLED }
	
	public ResultFuture() {
		super();
	}
	
	@Override
	public boolean isDone() {
		return status != ResultStatus.INITIAL;
	}
	
	public boolean isCompleted() {
		return isDone();
	}
	
	@Override
	public boolean isCancelled() {
		return status == ResultStatus.CANCELLED;
	}
	
	public CountDownLatch getCompletionLatch() {
		return completionLatch;
	}
	
	public void setResult(DATA resultValue) {
		setResult(Result.fromValue(resultValue));
	}
	
	public void setExceptionResult(EXC exceptionResult) {
		setResult(Result.fromException(exceptionResult));
	}
	
	public void setRuntimeExceptionResult(RuntimeException exceptionResult) {
		setResult(Result.fromRuntimeException(exceptionResult));
	}
	
	public void setResult(Result<DATA, EXC> newResult) {
		synchronized (lock) {
			if(isDone()) {
				handleReSetResult();
				return;
			}
			result = newResult;
			status = ResultStatus.RESULT_SET;
			completionLatch.countDown();
		}
	}
	
	@Override
	public boolean cancel() {
		synchronized (lock) {
			if(isDone()) {
				return false;
			} else {
				status = ResultStatus.CANCELLED;
				completionLatch.countDown();
				return true;
			}
		}
	}
	
	protected void handleReSetResult() {
		throw assertFail();
	}
	
	public void awaitCompletion() throws InterruptedException {
		completionLatch.await();
	}
	
	public void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
		boolean success = completionLatch.await(timeout, unit);
		if(!success) {
			throw new TimeoutException();
		}
	}
	
	/* FIXME: rewrite */
	public Result<DATA, EXC> getRawResult() {
		return result;
	}
	
	@Override
	public DATA awaitResult() 
			throws EXC, OperationCancellation, InterruptedException {
		awaitCompletion();
		return getResult_afterCompletion();
	}
	
	@Override
	public DATA awaitResult(long timeout, TimeUnit unit) 
			throws EXC, OperationCancellation, InterruptedException, TimeoutException {
		awaitCompletion(timeout, unit);
		return getResult_afterCompletion();
	}
	
	protected DATA getResult_afterCompletion() throws EXC, OperationCancellation {
		if(isCancelled()) {
			throw new OperationCancellation();
		}
		return result.get();
	}
	
	/* -----------------  ----------------- */
	
	public static class LatchFuture extends ResultFuture<Object, RuntimeException> {
		
		public void setCompleted() {
			setResult(null);
		}
		
		@Override
		protected void handleReSetResult() {
			// Do nothing - this is allowed because the possible value is always null anyways
		}
		
	}
	
}
package com.gathin.tvhome;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Failure backoff is charged on completion; lifecycle cancellation is immediately retryable. */
final class ArchiveRetry {
    private final long delay;
    private boolean running;
    private long next;
    ArchiveRetry(long delay) { this.delay=delay; }
    synchronized boolean begin(long now) {
        if (running || now<next) return false;
        running=true; return true;
    }
    synchronized void finish(long now, boolean success, boolean cancelled) {
        next=success || cancelled ? 0 : now+delay;
        running=false;
    }
    <T> T complete(LongSupplier clock,BooleanSupplier cancelled,Callable<T> request) throws Exception {
        boolean success=false;
        try {T result=request.call();success=true;return result;}
        finally {finish(clock.getAsLong(),success,cancelled.getAsBoolean() || Thread.currentThread().isInterrupted());}
    }
}

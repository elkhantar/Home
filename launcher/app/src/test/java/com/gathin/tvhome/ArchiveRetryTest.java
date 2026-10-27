package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveRetryTest {
    @Test public void cancelledColdFetchCanResumeImmediately() {
        ArchiveRetry retry=new ArchiveRetry(30*60_000L);
        assertTrue(retry.begin(10)); assertFalse(retry.begin(11));
        retry.finish(12,false,true);
        assertTrue(retry.begin(13));
        retry.finish(14,true,false);
        assertTrue(retry.begin(15));
    }
    @Test public void realFailureStillBacksOffFromCompletion() {
        ArchiveRetry retry=new ArchiveRetry(100);
        assertTrue(retry.begin(10)); retry.finish(40,false,false);
        assertFalse(retry.begin(139)); assertTrue(retry.begin(140));
    }
    @Test public void cancelledIoCanClearTheInterruptFlagWithoutChargingFailureBackoff() throws Exception {
        ArchiveRetry retry=new ArchiveRetry(30*60_000L);
        java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        assertTrue(retry.begin(10));
        try {
            retry.complete(()->12,cancelled::get,()->{
                cancelled.set(true);
                Thread.interrupted();
                throw new java.io.IOException("Socket read cancelled");
            });
            fail("I/O failure expected");
        } catch(java.io.IOException expected) { }
        assertTrue(retry.begin(13));
    }
}

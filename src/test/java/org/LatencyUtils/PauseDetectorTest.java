/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package org.LatencyUtils;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JUnit test for {@link org.LatencyUtils.PauseDetector}
 */
public class PauseDetectorTest {

    static {
        System.setProperty("LatencyUtils.useActualTime", "false");
    }

    @Test
    public void testListenerExceptionDoesNotStopPauseDetector() throws Exception {
        // A pause detector whose pauses are injected directly by the test (notifyListeners is
        // protected, and so accessible from this package):
        PauseDetector pauseDetector = new PauseDetector() {};

        final AtomicBoolean listenerThrew = new AtomicBoolean(false);
        final CountDownLatch secondPauseDelivered = new CountDownLatch(1);

        PauseDetectorListener failingListener = new PauseDetectorListener() {
            public void handlePauseEvent(final long pauseLength, final long pauseEndTime) {
                if (listenerThrew.compareAndSet(false, true)) {
                    throw new ArrayIndexOutOfBoundsException("value outside of histogram covered range");
                }
            }
        };

        PauseDetectorListener deliveryProbe = new PauseDetectorListener() {
            public void handlePauseEvent(final long pauseLength, final long pauseEndTime) {
                if (pauseLength == 2000L) {
                    secondPauseDelivered.countDown();
                }
            }
        };

        try {
            pauseDetector.addListener(failingListener, true /* high priority */);
            pauseDetector.addListener(deliveryProbe);

            // The first pause makes a listener throw on the pause detector thread:
            pauseDetector.notifyListeners(1000L, 1000L);
            // The pause detector thread must survive it and keep delivering pauses:
            pauseDetector.notifyListeners(2000L, 2000L);

            Assert.assertTrue("Pause detector thread stopped delivering pauses after a listener threw",
                    secondPauseDelivered.await(10, TimeUnit.SECONDS));
            Assert.assertTrue("Listener should have thrown on the first pause", listenerThrew.get());
        } finally {
            pauseDetector.shutdown();
        }
    }
}

/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper;

import io.brokerqe.claire.exception.ClaireRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public final class TimeHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeHelper.class);

    private TimeHelper() {
        super();
    }

    public static <R> R retry(ThrowableFunction<R> func, R expectedReturn, long retries, long pollMs) {
        long currentRetry = 1;
        LOGGER.debug("Retrying for {} times with polls of {} ms", retries, pollMs);
        R returnedValue = null;
        while (currentRetry <= retries) {
            try {
                returnedValue = func.perform();
                if (expectedReturn.equals(returnedValue)) {
                    return returnedValue;
                }
                currentRetry++;
            } catch (Exception e) {
                String errMsg = String.format("error on executing code for retry: %s", e.getMessage());
                LOGGER.error(errMsg, e);
                throw new ClaireRuntimeException(errMsg, e);
            }

            LOGGER.trace("Waiting for {} ms before try again", pollMs);
            try {
                TimeUnit.MILLISECONDS.sleep(pollMs);
            } catch (InterruptedException e) {
                String errMsg = String.format("error on sleeping: %s", e.getMessage());
                LOGGER.error(errMsg, e);
                throw new ClaireRuntimeException(errMsg, e);
            }
        }
        LOGGER.debug("Retries of {} exceeded", retries);
        return returnedValue;
    }

    public static void waitFor(ThrowablePredicate<Boolean> predicate, long pollMs, long timeoutInMs) {
        long realTimeout = System.currentTimeMillis() + timeoutInMs;
        LOGGER.debug("Waiting for during {} ms with polls of {} ms", timeoutInMs, pollMs);
        while (System.currentTimeMillis() < realTimeout) {
            try {
                if (predicate.test(true)) {
                    long timeSpent = timeoutInMs - (realTimeout - System.currentTimeMillis());
                    LOGGER.debug("{} ms spent waiting", timeSpent);
                    return;
                }
            } catch (Exception e) {
                String errMsg = String.format("error on executing code for waitFor: %s", e.getMessage());
                LOGGER.error(errMsg, e);
                throw new ClaireRuntimeException(errMsg, e);
            }

            LOGGER.trace("Waiting for {} ms before try again", pollMs);
            try {
                TimeUnit.MILLISECONDS.sleep(pollMs);
            } catch (InterruptedException e) {
                String errMsg = String.format("error on sleeping: %s", e.getMessage());
                LOGGER.error(errMsg, e);
                throw new ClaireRuntimeException(errMsg, e);
            }
        }
        LOGGER.debug("Timeout of {} exceeded", timeoutInMs);
    }

    public static void waitFor(long delay)  {
        if (delay > 0) {
            try {
                LOGGER.debug("Sleeping for {} ms", delay);
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                String errMsg = String.format("Error on sleeping: %s", e.getMessage());
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, e);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowablePredicate<T> {
        boolean test(T t) throws Exception;
    }

    @FunctionalInterface
    public interface ThrowableFunction<R> {
        R perform() throws Exception;
    }
}
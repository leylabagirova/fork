/*
 * Copyright 2019 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.runner.listeners;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.shazam.fork.device.DeviceTestFilesCleaner;
import com.shazam.fork.model.Device;
import com.shazam.fork.model.Pool;
import com.shazam.fork.model.TestCaseEvent;
import com.shazam.fork.runner.FailedTestScheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

public class RetryListener extends NoOpITestRunListener {
    private static final Logger logger = LoggerFactory.getLogger(RetryListener.class);
    private final Device device;
    private final FailedTestScheduler failedTestScheduler;
    private final Pool pool;
    private final DeviceTestFilesCleaner deviceTestFilesCleaner;
    @Nonnull
    private final TestCaseEvent runningTestCase;

    private TestIdentifier startedTest;
    private TestIdentifier failedTest;

    public RetryListener(Pool pool,
                         Device device,
                         FailedTestScheduler failedTestScheduler,
                         DeviceTestFilesCleaner deviceTestFilesCleaner,
                         @Nonnull TestCaseEvent runningTestCase) {
        this.failedTestScheduler = failedTestScheduler;
        this.device = checkNotNull(device);
        this.pool = checkNotNull(pool);
        this.deviceTestFilesCleaner = deviceTestFilesCleaner;
        this.runningTestCase = checkNotNull(runningTestCase);
    }

    @Override
    public void testStarted(TestIdentifier test) {
        startedTest = test;
    }

    @Override
    public void testFailed(TestIdentifier test, String trace) {
        failedTest = test;
    }

    @Override
    public void testRunFailed(String errorMessage) {
        logger.info("Test run failed due to a fatal error: " + errorMessage);
        if (failedTest == null && startedTest != null) {
            System.out.println("Reschedule a test started by this test run");
            rescheduleTestExecution();
        }
    }

    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        if (failedTest != null) {
            rescheduleTestExecution();
        }
    }

    private void rescheduleTestExecution() {
        if (failedTestScheduler.rescheduleTestExecution(runningTestCase)) {
            logger.info("Test " + runningTestCase.getTestFullName() + " enqueued again into pool:" + pool.getName());
            removeFailureTraceFiles(runningTestCase);
        } else {
            logger.info("Test " + runningTestCase.getTestFullName() + " failed on device " + device.getSafeSerial()
                    + " but retry is not allowed.");
        }
    }

    private void removeFailureTraceFiles(TestCaseEvent testCase) {
        boolean isDeleted = deviceTestFilesCleaner.deleteTraceFiles(testCase);
        if (!isDeleted) {
            logger.warn("Failed to remove a trace filed for a failed but enqueued again test");
        }
    }
}

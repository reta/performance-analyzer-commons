/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.commons.metrics;


import java.util.HashMap;
import java.util.Map;

public class MetricsConfiguration {
    public static final int SAMPLING_INTERVAL = 5000;
    public static final int ROTATION_INTERVAL = 30000;
    public static final int STATS_ROTATION_INTERVAL = 60000;

    public static class MetricConfig {
        public int samplingInterval;
        public int rotationInterval;

        public MetricConfig(int samplingInterval, int rotationInterval) {
            this.samplingInterval = samplingInterval;
            this.rotationInterval = rotationInterval;
        }
    }
    /*
    With this refactoring, the CONFIG_MAP initialization has been moved out
    and will be statically initialized within the PerformanceAnalyzerApp
     */
    public static final Map<Class, MetricConfig> CONFIG_MAP = new HashMap<>();
    public static final MetricConfig cdefault;

    static {
        cdefault = new MetricConfig(SAMPLING_INTERVAL, 0);
        CONFIG_MAP.put(PerformanceAnalyzerMetrics.class, new MetricConfig(0, ROTATION_INTERVAL));
    }
}
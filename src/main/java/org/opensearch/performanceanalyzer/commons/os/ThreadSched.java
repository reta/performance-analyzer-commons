/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.commons.os;


import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.opensearch.performanceanalyzer.commons.metrics_generator.SchedMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.metrics_generator.linux.LinuxSchedMetricsGenerator;

public final class ThreadSched {
    private static final Logger LOGGER = LogManager.getLogger(ThreadSched.class);
    public static final ThreadSched INSTANCE = new ThreadSched();
    private String pid = null;
    private List<String> tids = null;
    private Map<String, Map<String, Object>> tidKVMap = new HashMap<>();
    private Map<String, Map<String, Object>> oldtidKVMap = new HashMap<>();
    private long kvTimestamp = 0;
    private long oldkvTimestamp = 0;

    public static class SchedMetrics {
        public final double avgRuntime;
        public final double avgWaittime;
        public final double contextSwitchRate; // both voluntary and involuntary

        SchedMetrics(double avgRuntime, double avgWaittime, double contextSwitchRate) {
            this.avgRuntime = avgRuntime;
            this.avgWaittime = avgWaittime;
            this.contextSwitchRate = contextSwitchRate;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.avgRuntime, this.avgWaittime, this.contextSwitchRate);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SchedMetrics other = (SchedMetrics) o;

            return Double.compare(avgRuntime, other.avgRuntime) == 0
                    && Double.compare(avgWaittime, other.avgWaittime) == 0
                    && Double.compare(contextSwitchRate, other.contextSwitchRate) == 0;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("avgruntime: ")
                    .append(avgRuntime)
                    .append(" avgwaittime: ")
                    .append(avgWaittime)
                    .append(" ctxrate: ")
                    .append(contextSwitchRate)
                    .toString();
        }
    }

    private LinuxSchedMetricsGenerator schedLatencyMap = new LinuxSchedMetricsGenerator();

    private static String[] schedKeys = {"runticks", "waitticks", "totctxsws"};

    private static SchemaFileParser.FieldTypes[] schedTypes = {
        SchemaFileParser.FieldTypes.ULONG,
        SchemaFileParser.FieldTypes.ULONG,
        SchemaFileParser.FieldTypes.ULONG
    };

    private ThreadSched() {
        try {
            pid = OSGlobals.getPid();
            tids = OSGlobals.getTids();
        } catch (Exception e) {
            LOGGER.error(
                    (Supplier<?>)
                            () ->
                                    new ParameterizedMessage(
                                            "Error In Initializing ThreadCPU: {}", e.toString()),
                    e);
        }
    }

    public synchronized void addSample() {
        tids = OSGlobals.getTids();

        oldtidKVMap.clear();
        oldtidKVMap.putAll(tidKVMap);

        tidKVMap.clear();
        oldkvTimestamp = kvTimestamp;
        kvTimestamp = System.currentTimeMillis();
        for (String tid : tids) {
            Map<String, Object> sample =
                    (new SchemaFileParser(
                                    "/proc/" + pid + "/task/" + tid + "/schedstat",
                                    schedKeys,
                                    schedTypes))
                            .parse();
            tidKVMap.put(tid, sample);
        }

        calculateSchedLatency();
    }

    private void calculateSchedLatency() {
        if (oldkvTimestamp == kvTimestamp) {
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : tidKVMap.entrySet()) {
            Map<String, Object> v = entry.getValue();
            Map<String, Object> oldv = oldtidKVMap.get(entry.getKey());
            if (v != null && oldv != null) {
                if (!v.containsKey("totctxsws") || !oldv.containsKey("totctxsws")) {
                    continue;
                }
                long ctxdiff =
                        (long) v.getOrDefault("totctxsws", 0L)
                                - (long) oldv.getOrDefault("totctxsws", 0L);
                double avgRuntime =
                        1.0e-9
                                * ((long) v.getOrDefault("runticks", 0L)
                                        - (long) oldv.getOrDefault("runticks", 0L));
                double avgWaittime =
                        1.0e-9
                                * ((long) v.getOrDefault("waitticks", 0L)
                                        - (long) oldv.getOrDefault("waitticks", 0L));
                if (ctxdiff == 0) {
                    avgRuntime = 0;
                    avgWaittime = 0;
                } else {
                    avgRuntime /= 1.0 * ctxdiff;
                    avgWaittime /= 1.0 * ctxdiff;
                }
                double contextSwitchRate = ctxdiff;
                contextSwitchRate /= 1.0e-3 * (kvTimestamp - oldkvTimestamp);

                schedLatencyMap.setSchedMetric(
                        entry.getKey(),
                        new SchedMetrics(avgRuntime, avgWaittime, contextSwitchRate));
            }
        }
    }

    public synchronized SchedMetricsGenerator getSchedLatency() {
        return schedLatencyMap;
    }

    @VisibleForTesting
    public Map<String, Map<String, Object>> getTidKVMap() {
        return this.tidKVMap;
    }
}

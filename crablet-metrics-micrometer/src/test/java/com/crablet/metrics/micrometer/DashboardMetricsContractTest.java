package com.crablet.metrics.micrometer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that every Prometheus metric name referenced in the Grafana dashboard
 * resolves to a known Crablet metric constant.
 * <p>
 * Reads the dashboard JSON directly from the observability/ directory (one level up
 * from the module root, which is Maven's working directory when running tests).
 * If the file is absent (e.g., isolated module build), the test is skipped gracefully.
 * <p>
 * When a metric is renamed in the collector, this test fails — forcing the dashboard
 * to be updated before the rename ships.
 * <p>
 * Strategy: build the set of expected Prometheus names from CrabletMetricNames constants
 * (Micrometer name → dots-to-underscores + possible suffixes), then check each dashboard
 * metric against that set. This avoids the ambiguous reverse transformation.
 */
@SuppressWarnings("NullAway")
@DisplayName("Dashboard metrics contract")
class DashboardMetricsContractTest {

    // Relative to crablet-metrics-micrometer/ — Maven's working dir is the module directory.
    // One ".." reaches the project root (spring-crablet/), then down into observability/.
    private static final Path DASHBOARD_PATH = Path.of(
        "../observability/grafana/dashboards/crablet-dashboard.json"
    );

    @Test
    @DisplayName("all dashboard metric references resolve to CrabletMetricNames constants")
    void allDashboardMetricsAreKnown() throws IOException, IllegalAccessException {
        assumeTrue(Files.exists(DASHBOARD_PATH),
            "Dashboard file not found at " + DASHBOARD_PATH.toAbsolutePath() + " — skipping contract test");

        String json = Files.readString(DASHBOARD_PATH);

        Set<String> dashboardNames = extractPrometheusMetricNames(json);
        Set<String> knownPrometheusNames = buildKnownPrometheusNames();

        Set<String> unknown = new HashSet<>(dashboardNames);
        unknown.removeAll(knownPrometheusNames);

        assertThat(unknown)
            .as("Dashboard references metric names not found in CrabletMetricNames.\n" +
                "Either rename the metric in the dashboard or add it to CrabletMetricNames.")
            .isEmpty();
    }

    /**
     * Derives all expected Prometheus metric names from CrabletMetricNames constants.
     * For each Micrometer name (dots-separated), the Prometheus registry:
     * - Replaces dots with underscores
     * - For counters: appends _total (unless name already ends in _total)
     * - For timers: appends _seconds_count, _seconds_sum, _seconds_bucket, _seconds_max
     * - For gauges: no suffix
     * We generate all variants so the test works regardless of meter type.
     */
    private Set<String> buildKnownPrometheusNames() throws IllegalAccessException {
        Set<String> known = new HashSet<>();
        for (Field field : CrabletMetricNames.class.getDeclaredFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            String micrometerName = (String) field.get(null);
            String base = micrometerName.replace('.', '_');

            known.add(base);                              // gauge / raw
            if (base.endsWith("_total")) {
                known.add(base);                          // counter already ending in _total (no double-add)
            } else {
                known.add(base + "_total");               // counter
            }
            known.add(base + "_seconds_count");           // timer
            known.add(base + "_seconds_sum");             // timer
            known.add(base + "_seconds_bucket");          // timer
            known.add(base + "_seconds_max");             // timer
        }
        return known;
    }

    private Set<String> extractPrometheusMetricNames(String json) {
        Set<String> names = new HashSet<>();
        Pattern exprField = Pattern.compile("\"expr\"\\s*:\\s*\"([^\"]+)\"");
        Matcher exprMatcher = exprField.matcher(json);
        while (exprMatcher.find()) {
            String expr = exprMatcher.group(1).replace("\\n", " ");
            Pattern metricName = Pattern.compile("\\b([a-z][a-z0-9_]{4,})\\b");
            Matcher nameMatcher = metricName.matcher(expr);
            while (nameMatcher.find()) {
                String candidate = nameMatcher.group(1);
                if (!isPromQLKeyword(candidate)) {
                    names.add(candidate);
                }
            }
        }
        return names;
    }

    private boolean isPromQLKeyword(String s) {
        return PROMQL_KEYWORDS.contains(s);
    }

    private static final Set<String> PROMQL_KEYWORDS = new HashSet<>(Arrays.asList(
        "rate", "irate", "increase", "sum", "count", "avg", "min", "max", "by", "without",
        "offset", "bool", "group_left", "group_right", "ignoring", "on", "unless", "and", "or",
        "histogram_quantile", "label_replace", "label_join", "vector", "scalar",
        "delta", "deriv", "predict_linear", "absent", "changes", "resets",
        "round", "ceil", "floor", "clamp", "clamp_max", "clamp_min",
        "instance_id", "processor", "command_type", "event_type", "operation_type",
        "error_type", "publisher", "view", "automation", "quantile"
    ));
}

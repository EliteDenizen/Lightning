package com.denizenmc.lightning.metrics;

import java.util.function.BiConsumer;

/**
 * Base class for custom charts defined by plugin authors. Each subclass wraps a callback
 * supplied by the plugin, which is invoked once per submission. If the callback throws
 * an exception, the chart is skipped for that submission without interrupting the
 * remaining metrics or affecting the host plugin.
 *
 * <p>Each chart has a type and an optional title. If the chart is also defined on the
 * ArrowStats site, the server uses its stored configuration. The type and title provided
 * here serve as fallback values for charts registered directly through code.</p>
 */
public abstract class CustomChart {

    private final String chartId;
    private final String title;

    protected CustomChart(String chartId) {
        this(chartId, null);
    }

    protected CustomChart(String chartId, String title) {
        if (chartId == null || chartId.isEmpty()) {
            throw new IllegalArgumentException("chartId must not be null or empty");
        }
        this.chartId = chartId;
        this.title = title;
    }

    String getChartId() {
        return chartId;
    }

    /**
     * Builds the chart entry containing its type, optional title, and data.
     * Returns null when the chart has no data or its callback fails.
     */
    JsonObjectBuilder.JsonObject getRequestJsonObject(BiConsumer<String, Throwable> errorLogger) {
        JsonObjectBuilder builder = new JsonObjectBuilder();
        builder.appendField("type", getType());
        if (title != null) {
            builder.appendField("title", title);
        }
        try {
            if (!appendData(builder)) {
                return null;
            }
        } catch (Throwable t) {
            errorLogger.accept("Failed to get data for custom chart with id " + chartId, t);
            return null;
        }
        return builder.build();
    }

    /** Returns the server-side chart type defined by the ArrowStats ChartType constants. */
    protected abstract String getType();

    /**
     * Appends the chart's data field to the builder.
     * Return false to skip the chart, such as when the callback returns null or empty data.
     */
    protected abstract boolean appendData(JsonObjectBuilder builder) throws Exception;
}
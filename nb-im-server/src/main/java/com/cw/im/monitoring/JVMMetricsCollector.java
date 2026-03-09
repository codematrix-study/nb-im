package com.cw.im.monitoring;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JVM指标采集器
 *
 * <p>负责采集JVM相关的性能指标</p>
 *
 * <h3>采集指标</h3>
 * <ul>
 *     <li>内存指标：堆内存、非堆内存使用情况</li>
 *     <li>线程指标：线程数、峰值线程数</li>
 *     <li>GC指标：GC次数、GC时间</li>
 *     <li>CPU指标：CPU使用率</li>
 *     <li>运行时指标：JVM启动时间、运行时长</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class JVMMetricsCollector extends AbstractMetricsCollector {

    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final OperatingSystemMXBean operatingSystemMXBean;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final long startTime;

    /**
     * 构造函数
     */
    public JVMMetricsCollector() {
        super("jvm", "JVM性能指标采集器");
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    @Override
    public Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // 1. 内存指标
            metrics.putAll(collectMemoryMetrics());

            // 2. 线程指标
            metrics.putAll(collectThreadMetrics());

            // 3. GC指标
            metrics.putAll(collectGCMetrics());

            // 4. CPU指标
            metrics.putAll(collectCPUMetrics());

            // 5. 运行时指标
            metrics.putAll(collectRuntimeMetrics());

        } catch (Exception e) {
            log.error("采集JVM指标异常", e);
        }

        return metrics;
    }

    /**
     * 采集内存指标
     */
    private Map<String, Object> collectMemoryMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 堆内存
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        metrics.put("jvm_memory_heap_used", heapUsage.getUsed());
        metrics.put("jvm_memory_heap_max", heapUsage.getMax());
        metrics.put("jvm_memory_heap_committed", heapUsage.getCommitted());
        metrics.put("jvm_memory_heap_init", heapUsage.getInit());
        metrics.put("jvm_memory_heap_usage_percent",
                calculateUsagePercent(heapUsage.getUsed(), heapUsage.getMax()));

        // 非堆内存
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        metrics.put("jvm_memory_non_heap_used", nonHeapUsage.getUsed());
        metrics.put("jvm_memory_non_heap_max", nonHeapUsage.getMax());
        metrics.put("jvm_memory_non_heap_committed", nonHeapUsage.getCommitted());
        metrics.put("jvm_memory_non_heap_init", nonHeapUsage.getInit());

        return metrics;
    }

    /**
     * 采集线程指标
     */
    private Map<String, Object> collectThreadMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("jvm_threads_count", threadMXBean.getThreadCount());
        metrics.put("jvm_threads_peak", threadMXBean.getPeakThreadCount());
        metrics.put("jvm_threads_daemon", threadMXBean.getDaemonThreadCount());
        metrics.put("jvm_threads_total_started", threadMXBean.getTotalStartedThreadCount());

        // 死锁线程检测（如果支持）
        try {
            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
            metrics.put("jvm_threads_deadlocked",
                    deadlockedThreads == null ? 0 : deadlockedThreads.length);
        } catch (Exception e) {
            metrics.put("jvm_threads_deadlocked", -1);
        }

        return metrics;
    }

    /**
     * 采集GC指标
     */
    private Map<String, Object> collectGCMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        long totalGCCount = 0;
        long totalGCTime = 0;

        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();

            totalGCCount += count;
            totalGCTime += time;

            String name = gcBean.getName().replace(" ", "_");
            metrics.put("jvm_gc_" + name + "_count", count);
            metrics.put("jvm_gc_" + name + "_time", time);
        }

        metrics.put("jvm_gc_total_count", totalGCCount);
        metrics.put("jvm_gc_total_time", totalGCTime);

        return metrics;
    }

    /**
     * 采集CPU指标
     */
    private Map<String, Object> collectCPUMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 可用处理器数
        metrics.put("jvm_cpu_available_processors", operatingSystemMXBean.getAvailableProcessors());

        // 系统负载平均值
        try {
            double loadAverage = operatingSystemMXBean.getSystemLoadAverage();
            metrics.put("jvm_cpu_load_average", loadAverage >= 0 ? loadAverage : 0);
        } catch (Exception e) {
            metrics.put("jvm_cpu_load_average", 0);
        }

        return metrics;
    }

    /**
     * 采集运行时指标
     */
    private Map<String, Object> collectRuntimeMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        metrics.put("jvm_runtime_uptime", uptime);
        metrics.put("jvm_runtime_start_time", startTime);

        return metrics;
    }

    /**
     * 计算内存使用率百分比
     */
    private double calculateUsagePercent(long used, long max) {
        if (max <= 0) {
            return 0;
        }
        return (double) used / max * 100;
    }
}

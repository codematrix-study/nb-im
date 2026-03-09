package com.cw.im.monitoring;

import java.util.Map;

/**
 * 指标采集器接口
 *
 * <p>定义指标采集的统一接口，所有指标采集器都需要实现此接口</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *     <li>统一的指标采集接口</li>
 *     <li>支持多种指标类型（计数器、仪表盘、直方图等）</li>
 *     <li>支持指标标签和维度</li>
 *     <li>线程安全的指标采集</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
public interface MetricsCollector {

    /**
     * 采集指标
     *
     * @return 指标数据
     */
    Map<String, Object> collectMetrics();

    /**
     * 获取采集器名称
     *
     * @return 采集器名称
     */
    String getName();

    /**
     * 获取采集器描述
     *
     * @return 描述信息
     */
    String getDescription();

    /**
     * 启动采集器
     */
    void start();

    /**
     * 停止采集器
     */
    void stop();

    /**
     * 检查采集器是否运行中
     *
     * @return true-运行中, false-已停止
     */
    boolean isRunning();
}

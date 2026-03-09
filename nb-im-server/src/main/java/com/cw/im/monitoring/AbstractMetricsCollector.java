package com.cw.im.monitoring;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指标采集器抽象基类
 *
 * <p>提供指标采集器的基础实现，包括生命周期管理和通用功能</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *     <li>生命周期管理（启动/停止）</li>
 *     <li>运行状态检查</li>
 *     <li>统一的日志记录</li>
 *     <li>资源清理钩子</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractMetricsCollector implements MetricsCollector {

    /**
     * 运行状态标志
     */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 采集器名称
     */
    protected final String name;

    /**
     * 采集器描述
     */
    protected final String description;

    /**
     * 构造函数
     *
     * @param name        采集器名称
     * @param description 采集器描述
     */
    protected AbstractMetricsCollector(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("指标采集器启动: name={}", name);
            doStart();
        } else {
            log.warn("指标采集器已在运行中: name={}", name);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("指标采集器停止: name={}", name);
            doStop();
        } else {
            log.warn("指标采集器未在运行: name={}", name);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 启动采集器（子类实现具体逻辑）
     */
    protected void doStart() {
        // 默认空实现，子类可以覆盖
    }

    /**
     * 停止采集器（子类实现具体逻辑）
     */
    protected void doStop() {
        // 默认空实现，子类可以覆盖
    }
}

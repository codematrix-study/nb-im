package com.cw.im.common.protocol;

/**
 * 协议版本常量
 *
 * <p>定义IM系统的协议版本号，用于协议升级和兼容性处理</p>
 *
 * @author cw
 * @since 1.0.0
 */
public final class ProtocolVersion {

    private ProtocolVersion() {
        // 防止实例化
    }

    /**
     * 当前协议版本
     */
    public static final String CURRENT_VERSION = "1.0";

    /**
     * 协议版本历史（用于向后兼容）
     */
    public static final String VERSION_1_0 = "1.0";

    /**
     * 获取当前协议版本
     *
     * @return 当前版本号
     */
    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    /**
     * 检查版本是否支持
     *
     * @param version 版本号
     * @return true-支持, false-不支持
     */
    public static boolean isSupported(String version) {
        return VERSION_1_0.equals(version);
    }
}

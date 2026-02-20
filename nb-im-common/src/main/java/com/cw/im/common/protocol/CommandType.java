package com.cw.im.common.protocol;

/**
 * @author thisdcw-macAir@gmail.com
 * @date 2026/2/13 22:12
 */
public enum CommandType {

    /**
     * 私聊消息
     */
    PRIVATE_CHAT(1, "私聊"),

    /**
     * 群聊消息
     */
    GROUP_CHAT(2, "群聊"),

    /**
     * 公屏消息
     */
    PUBLIC_CHAT(3, "公屏"),

    /**
     * 系统通知
     */
    SYSTEM_NOTICE(4, "系统通知"),

    /**
     * ACK 确认
     */
    ACK(5, "确认"),

    /**
     * 心跳
     */
    HEARTBEAT(6, "心跳");

    private final int code;
    private final String desc;

    CommandType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static CommandType fromCode(int code) {
        for (CommandType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown command code: " + code);
    }

}

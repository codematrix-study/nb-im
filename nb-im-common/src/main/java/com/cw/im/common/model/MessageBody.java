package com.cw.im.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IM消息体
 *
 * <p>包含消息的实际内容数据</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageBody implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息内容
     * <p>文本消息、图片URL、文件信息等，具体格式由业务层定义</p>
     */
    private String content;

    /**
     * 消息类型（业务层定义）
     * <p>如：text、image、file、voice、video等</p>
     */
    private String contentType;

    /**
     * 扩展字段
     * <p>用于存储额外的业务信息，如：</p>
     * <ul>
     *     <li>文件大小、时长等多媒体信息</li>
     *     <li>消息引用/回复信息</li>
     *     <li>@提醒信息</li>
     *     <li>自定义业务字段</li>
     * </ul>
     */
    private java.util.Map<String, Object> extras;
}

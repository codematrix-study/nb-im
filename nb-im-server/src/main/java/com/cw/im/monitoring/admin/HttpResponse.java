package com.cw.im.monitoring.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP响应
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpResponse {

    /**
     * 响应码
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private Object data;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 成功响应
     */
    public static HttpResponse success(Object data) {
        return HttpResponse.builder()
                .code(200)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 失败响应
     */
    public static HttpResponse error(int code, String message) {
        return HttpResponse.builder()
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 404响应
     */
    public static HttpResponse notFound() {
        return error(404, "Not Found");
    }

    /**
     * 500响应
     */
    public static HttpResponse serverError(String message) {
        return error(500, message);
    }

    /**
     * 400响应
     */
    public static HttpResponse badRequest(String message) {
        return error(400, message);
    }
}

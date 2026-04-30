package com.basiclab.iot.video.sdk.dto;

import java.util.LinkedHashMap;

/**
 * 精简版接口响应体，保持与原 AjaxResult 近似的返回结构。
 */
public class AjaxResult extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    private static final String CODE = "code";
    private static final String MSG = "msg";
    private static final String DATA = "data";

    public static AjaxResult success(Object data) {
        AjaxResult result = new AjaxResult();
        result.put(CODE, 200);
        result.put(MSG, "success");
        result.put(DATA, data);
        return result;
    }

    public static AjaxResult error(String message) {
        AjaxResult result = new AjaxResult();
        result.put(CODE, 500);
        result.put(MSG, message);
        return result;
    }
}

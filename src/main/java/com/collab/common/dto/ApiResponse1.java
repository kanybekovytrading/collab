package com.collab.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class  ApiResponse1<T> {
    private boolean success;
    private String message;
    private T data;
    private Object errors;

    public static <T> ApiResponse1<T> ok(T data) {
        return ApiResponse1.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse1<T> ok(String message, T data) {
        return ApiResponse1.<T>builder().success(true).message(message).data(data).build();
    }

    public static <T> ApiResponse1<T> error(String message) {
        return ApiResponse1.<T>builder().success(false).message(message).build();
    }

    public static <T> ApiResponse1<T> error(String message, Object errors) {
        return ApiResponse1.<T>builder().success(false).message(message).errors(errors).build();
    }
}

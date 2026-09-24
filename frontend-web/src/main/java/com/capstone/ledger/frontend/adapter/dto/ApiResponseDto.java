package com.capstone.ledger.frontend.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Standard backend envelope response matching ApiResponse<T> in common module.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponseDto<T> {
    private boolean success;
    private String message;
    private T data;

    public ApiResponseDto() {}

    public ApiResponseDto(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}


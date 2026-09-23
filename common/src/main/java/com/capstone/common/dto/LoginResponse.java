package com.capstone.common.dto;

public record LoginResponse(String token, String tokenType, long expiresInSeconds, Long custId, String email) {

    public static LoginResponse of(String token, long expiresInSeconds, Long custId, String email) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, custId, email);
    }
}

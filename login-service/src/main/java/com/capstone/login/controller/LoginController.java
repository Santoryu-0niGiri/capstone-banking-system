
package com.capstone.login.controller;

import com.capstone.common.dto.ApiResponse;
import com.capstone.common.dto.LoginRequest;
import com.capstone.common.dto.LoginResponse;
import com.capstone.common.exception.InvalidTokenException;
import com.capstone.login.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String authorizationHeader) {
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization header must start with 'Bearer '");
        }
        String token = authorizationHeader.substring(7);
        loginService.logout(token);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful", null));
    }
}


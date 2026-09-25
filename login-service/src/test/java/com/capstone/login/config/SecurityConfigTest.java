package com.capstone.login.config;

import com.capstone.common.dto.LoginResponse;
import com.capstone.login.controller.LoginController;
import com.capstone.login.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoginController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private LoginService loginService;

        @MockBean
        private StringRedisTemplate redisTemplate;

        @Test
        @DisplayName("Verify CSRF is disabled and sessions are stateless")
        void testCsrfDisabledAndSessionStateless() throws Exception {
                Mockito.when(loginService.login(any()))
                                .thenReturn(LoginResponse.of("mock-token", 3600L, "cust-1", "user@test.com"));

                // Send a POST request without any CSRF token
                MvcResult result = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"test@example.com\",\"password\":\"dummy123\"}"))
                                // 1. Verify CSRF is disabled:
                                // If CSRF were enabled, Spring Security would block this mutating request with
                                // 403 Forbidden.
                                // With CSRF disabled, the request reaches the controller and succeeds with 200
                                // OK.
                                .andExpect(status().isOk())
                                // 2. Verify Stateless: No Set-Cookie (e.g. JSESSIONID) header is sent in the
                                // response
                                .andExpect(header().doesNotExist("Set-Cookie"))
                                .andReturn();

                // 3. Verify Stateless: No HttpSession is created on the request
                HttpServletRequest request = result.getRequest();
                assertThat(request.getSession(false))
                                .as("HttpSession should not be created when SessionCreationPolicy is STATELESS")
                                .isNull();
        }
}

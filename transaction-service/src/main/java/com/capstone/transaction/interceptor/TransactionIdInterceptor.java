package com.capstone.transaction.interceptor;

import com.capstone.common.constants.RedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.UUID;

/**
 * Captures or generates the transaction UUID for every inbound transaction
 * request.
 *
 * The UUID is:
 * 1. Read from the X-Transaction-Id header when supplied.
 * 2. Generated when the header is absent.
 * 3. Stored in Redis.
 * 4. Added to the HttpServletRequest as an attribute.
 *
 * The existing IdempotencyService remains responsible for the business-level
 * idempotency key and cached TransactionResponse.
 */
@Component
@RequiredArgsConstructor
public class TransactionIdInterceptor implements HandlerInterceptor {

    public static final String TRANSACTION_ID_ATTRIBUTE =
            "transactionId";

    public static final String TRANSACTION_ID_HEADER =
            "X-Transaction-Id";

    private static final Duration TRANSACTION_ID_TTL =
            Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        /*
         * Only generate transaction IDs for the mutation endpoint.
         *
         * This prevents GET /audit/{txnId} from unnecessarily creating
         * a new transaction UUID.
         */
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/v1/ledger/mutate".equals(
                        request.getRequestURI())) {

            return true;
        }

        String transactionId =
                request.getHeader(TRANSACTION_ID_HEADER);

        if (!StringUtils.hasText(transactionId)) {
            transactionId = UUID.randomUUID().toString();
        } else {
            /*
             * Validate a client-supplied transaction ID.
             */
            try {
                UUID.fromString(transactionId);
            } catch (IllegalArgumentException ex) {
                response.setStatus(
                        HttpServletResponse.SC_BAD_REQUEST);

                return false;
            }
        }

        /*
         * Store the transaction UUID in Redis.
         *
         * The Redis key is separate from the existing
         * idempotency:{key} mechanism.
         */
        redisTemplate.opsForValue().set(
                RedisKeys.transactionIdKey(transactionId),
                transactionId,
                TRANSACTION_ID_TTL
        );

        /*
         * Make the transaction ID available to the controller/service
         * without changing the request body.
         */
        request.setAttribute(
                TRANSACTION_ID_ATTRIBUTE,
                transactionId
        );

        /*
         * Echo the transaction ID back to the client.
         */
        response.setHeader(
                TRANSACTION_ID_HEADER,
                transactionId
        );

        return true;
    }
}
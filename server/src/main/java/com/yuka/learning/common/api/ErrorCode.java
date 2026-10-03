package com.yuka.learning.common.api;

import org.springframework.http.HttpStatus;

/**
 * Contract for all business error codes.
 * <p>
 * Code ranges are reserved per domain so new modules never collide:
 * <ul>
 *   <li>40000–49999 client errors (common)</li>
 *   <li>50000–59999 server errors (common)</li>
 *   <li>100000+ feature modules, 10000 per module (e.g. auth 100000–109999)</li>
 * </ul>
 */
public interface ErrorCode {

    int code();

    String message();

    HttpStatus httpStatus();
}

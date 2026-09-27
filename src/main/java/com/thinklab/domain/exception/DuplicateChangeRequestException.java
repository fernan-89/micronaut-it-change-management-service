package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an ChangeRequest is initiated with a serial number that already exists
 * within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateChangeRequestException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CHG-00409";

    public DuplicateChangeRequestException(String message) {
        super(ERROR_CODE, message);
    }
}

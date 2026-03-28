package com.loopers.infrastructure.support;

import org.springframework.dao.DataIntegrityViolationException;

public final class ConstraintViolationHelper {

    private ConstraintViolationHelper() {}

    public static boolean isUniqueViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                if (!"23000".equals(cve.getSQLState())) return false;
                String actual = cve.getConstraintName();
                if (actual == null) return false;
                int dotIndex = actual.lastIndexOf('.');
                if (dotIndex >= 0) {
                    actual = actual.substring(dotIndex + 1);
                }
                return constraintName.equalsIgnoreCase(actual);
            }
            cause = cause.getCause();
        }
        return false;
    }
}

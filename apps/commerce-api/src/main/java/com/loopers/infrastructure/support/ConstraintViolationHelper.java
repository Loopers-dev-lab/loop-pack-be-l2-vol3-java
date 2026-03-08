package com.loopers.infrastructure.support;

import org.springframework.dao.DataIntegrityViolationException;

public final class ConstraintViolationHelper {

    private ConstraintViolationHelper() {}

    /**
     * DataIntegrityViolationException이 특정 유니크 제약 위반인지 판별.
     * cause chain을 순회하여 Hibernate ConstraintViolationException을 찾고,
     * MySQL SQLState 23000 (integrity constraint violation) + constraintName으로 판별.
     * MySQL 8에서는 제약 이름이 "table.constraint_name" 형식으로 반환될 수 있어
     * 테이블 접두사를 제거 후 비교한다.
     * 판별 실패 시 false 반환 -> 호출부에서 원예외를 그대로 전파.
     */
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

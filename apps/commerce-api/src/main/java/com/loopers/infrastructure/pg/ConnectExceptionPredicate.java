package com.loopers.infrastructure.pg;

import java.net.ConnectException;
import java.util.function.Predicate;

public class ConnectExceptionPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ConnectException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

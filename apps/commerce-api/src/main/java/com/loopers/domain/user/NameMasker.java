package com.loopers.domain.user;

import org.springframework.stereotype.Component;

@Component
public class NameMasker {

    public String mask(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        if (name.length() == 1) {
            return "*";
        }

        return name.substring(0, name.length() - 1) + "*";
    }
}

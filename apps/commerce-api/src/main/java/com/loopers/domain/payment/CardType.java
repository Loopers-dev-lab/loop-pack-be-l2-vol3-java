package com.loopers.domain.payment;

import java.util.ArrayList;
import java.util.List;

public enum CardType {
    SAMSUNG("삼성카드"),
    HYUNDAI("현대카드"),
    KB("KB카드"),
    LOTTE("롯데카드");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String circuitBreakerKey(PgIdentifier pg) {
        return pg.name().toLowerCase() + "-" + this.name();
    }

    public static List<String> allCircuitBreakerKeys() {
        List<String> keys = new ArrayList<>();
        for (CardType cardType : values()) {
            for (PgIdentifier pg : PgIdentifier.values()) {
                keys.add(cardType.circuitBreakerKey(pg));
            }
        }
        return keys;
    }

    public enum PgIdentifier {
        PG1, PG2
    }
}

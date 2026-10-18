package com.example.learning

import org.junit.Assert.*
import org.junit.Test

class AccountScopeTest {
    @Test fun differentServersCannotShareLocalAccountEvenWithSameNumericId() {
        assertNotEquals(AccountScope.localId("https://school.test:8080", 1), AccountScope.localId("https://school.test:8081", 1))
        assertNotEquals(AccountScope.localId("https://school.test", 1), AccountScope.localId("https://school.test", 2))
        assertEquals(AccountScope.localId("https://school.test/", 1), AccountScope.localId("https://school.test", 1))
    }
}

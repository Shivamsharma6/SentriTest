package com.sentri.access_control;

import static org.junit.Assert.*;
import org.junit.Test;

public class BusinessRepositoryTest {
    @Test
    public void testBusinessRepositoryInstantiation() {
        BusinessRepository repo = new BusinessRepository();
        assertNotNull("BusinessRepository should be instantiated", repo);
    }
    // Add more tests for repository methods as needed.
}

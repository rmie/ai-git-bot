package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorTest {
    @Test
    void addingTwoNumbersWorksInJunit() {
        Calculator calculator = new Calculator();
        assertEquals(10, calculator.add(4, 6));
    }
}

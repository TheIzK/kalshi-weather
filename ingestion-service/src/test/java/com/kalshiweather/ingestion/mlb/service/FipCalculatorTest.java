package com.kalshiweather.ingestion.mlb.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FipCalculatorTest {

    private final FipCalculator calculator = new FipCalculator();

    @Test
    void parsesInningsPitchedWithOneOut() {
        assertEquals(new BigDecimal("142.3333"), calculator.parseInningsPitched("142.1"));
    }

    @Test
    void parsesInningsPitchedWithTwoOuts() {
        assertEquals(new BigDecimal("142.6667"), calculator.parseInningsPitched("142.2"));
    }

    @Test
    void parsesWholeInningsWithNoRemainder() {
        assertEquals(new BigDecimal("142"), calculator.parseInningsPitched("142.0"));
    }

    @Test
    void calculatesFipForKnownInputs() {
        // 15 HR, 40 BB, 5 HBP, 180 K over 180 IP -> a decent-ish starter
        BigDecimal fip = calculator.calculate(15, 40, 5, 180, new BigDecimal("180"));
        // (13*15 + 3*45 - 2*180) / 180 + 3.10 = (195 + 135 - 360)/180 + 3.10 = -0.1667 + 3.10
        assertEquals(new BigDecimal("2.9333"), fip);
    }
}

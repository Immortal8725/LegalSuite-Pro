package com.legalsuite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BankCsvTest {
    @Test
    void closingBalanceFromLastBalanceColumn() {
        String csv = """
                Date,Description,Amount,Balance
                2026-09-01,Opening,0,438250.00
                2026-09-02,Fee,-125.50,438124.50
                2026-09-03,Deposit,1000.00,439124.50
                """;
        BankCsv.Result result = BankCsv.parse(csv);
        assertEquals(3, result.rows().size());
        assertEquals(new BigDecimal("439124.50"), result.closingBalance());
        assertEquals("csv", result.source());
    }

    @Test
    void debitCreditColumns() {
        String csv = """
                Date,Narration,Debit,Credit,Balance
                2026-08-31,Balance brought forward,,,450000
                2026-09-01,Unidentified transfer,11750,,438250
                """;
        BankCsv.Result result = BankCsv.parse(csv);
        assertEquals(new BigDecimal("438250"), result.closingBalance());
    }

    @Test
    void emptyCsvRejected() {
        assertThrows(IllegalArgumentException.class, () -> BankCsv.parse(""));
    }
}

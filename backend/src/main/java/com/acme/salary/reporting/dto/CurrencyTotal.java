package com.acme.salary.reporting.dto;

import com.acme.salary.salary.Currency;
import java.math.BigDecimal;

public record CurrencyTotal(Currency currency, long headcount, BigDecimal totalCost) {}

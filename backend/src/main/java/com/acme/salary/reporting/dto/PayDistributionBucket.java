package com.acme.salary.reporting.dto;

import java.math.BigDecimal;

public record PayDistributionBucket(BigDecimal rangeStart, BigDecimal rangeEnd, long count) {}

package com.acme.salary.importexport.dto;

import java.util.List;

public record ImportSummary(int totalRows, int succeeded, int failed, List<ImportRowError> errors) {}

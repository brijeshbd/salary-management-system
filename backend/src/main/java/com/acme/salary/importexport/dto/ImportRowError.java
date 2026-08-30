package com.acme.salary.importexport.dto;

/** {@code row} is the 1-based data-row number (header excluded), matching how a spreadsheet user
 * would count rows in the file they uploaded. */
public record ImportRowError(int row, String message) {}

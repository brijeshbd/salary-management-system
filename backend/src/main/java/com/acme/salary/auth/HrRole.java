package com.acme.salary.auth;

/** Single role for v1 - modeled as a column rather than a roles table so a second role later is
 * a data change, not a schema change. */
public enum HrRole {
    HR_MANAGER
}

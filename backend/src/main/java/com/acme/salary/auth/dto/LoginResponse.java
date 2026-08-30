package com.acme.salary.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt) {}

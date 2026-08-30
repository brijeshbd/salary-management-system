package com.acme.salary.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("%s not found: %s".formatted(resourceName, id));
    }
}

package com.readshelf.system;

import java.time.Instant;

enum Status {
    UP,
    DOWN
}


public record HealthResponse(Status status, Instant timestamp) {
}

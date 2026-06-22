package com.readshelf.system;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;


@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public HealthResponse getHealth(){
        return new HealthResponse(Status.UP, Instant.now());
    }
}

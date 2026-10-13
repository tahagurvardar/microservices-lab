package com.example.storage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "storage-service",
                "status", "UP",
                "time", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable("name") String name) {
        return "Hello, " + name + "! Greetings from storage-service.";
    }

    @GetMapping("/simulate-error/{status}")
    public void simulateError(@PathVariable("status") int status) {
        if (status != 404 && status != 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Supported demo statuses are 404 and 500");
        }
        throw new ResponseStatusException(HttpStatus.valueOf(status),
                "Simulated downstream error");
    }
}

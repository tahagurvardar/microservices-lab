package com.example.client.controller;

import com.example.client.feign.StorageClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/client")
public class ClientController {

    private final StorageClient storageClient;

    public ClientController(StorageClient storageClient) {
        this.storageClient = storageClient;
    }

    @GetMapping("/ping-storage")
    public Map<String, Object> pingStorage() {
        Map<String, Object> storageAnswer = storageClient.ping();

        return Map.of(
                "service", "client-service",
                "storageAnswer", storageAnswer
        );
    }

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable("name") String name) {
        return storageClient.greet(name);
    }
}
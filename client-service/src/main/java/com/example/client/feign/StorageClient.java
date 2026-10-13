package com.example.client.feign;

import com.example.client.dto.ProcessedMessage;
import com.example.client.dto.StoredMessage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "storage-service",
        url = "${storage.service.url}"
)
public interface StorageClient {

    @GetMapping("/api/storage/ping")
    Map<String, Object> ping();

    @GetMapping("/api/storage/greet/{name}")
    String greet(@PathVariable("name") String name);

    @PostMapping("/api/storage/messages")
    StoredMessage saveMessage(
            @RequestBody ProcessedMessage message
    );

    @GetMapping("/api/storage/messages")
    List<StoredMessage> getAllMessages();

    @GetMapping("/api/storage/simulate-error/{status}")
    String simulateError(@PathVariable("status") int status);
}

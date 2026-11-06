package com.example.client.kafka;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/client/events")
public class ClientEventController {
    private final ClientEventPublisher publisher;

    public ClientEventController(ClientEventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<ClientEvent> publish(@RequestBody ClientEventRequest request) {
        if (request == null || request.clientId() == null || request.clientId().isBlank()
                || request.action() == null || request.action().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "clientId and action are required");
        }
        return ResponseEntity.accepted().body(publisher.publish(request));
    }
}

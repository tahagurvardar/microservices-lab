package com.example.storage.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findTop20BySentAtIsNullOrderByIdAsc();
}

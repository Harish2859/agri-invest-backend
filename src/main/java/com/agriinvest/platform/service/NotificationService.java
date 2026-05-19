package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.Notification;
import com.agriinvest.platform.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(String recipientEmail, String message) {
        Notification notification = new Notification();
        notification.setRecipientEmail(recipientEmail);
        notification.setMessage(message);
        return notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForRecipient(String recipientEmail) {
        return notificationRepository.findByRecipientEmailOrderByTimestampDesc(recipientEmail);
    }
}

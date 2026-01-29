package com.quhealthy.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccountDeletedEvent {
    private Long userId;
    private String email;
    private String role;
    private LocalDateTime deletedAt;
}
package com.collab.common.enums;

public enum ApplicationStatus {
    PENDING,              // Блогер подал заявку
    ACCEPTED,             // (deprecated) → сразу IN_WORK
    REJECTED,             // Бренд отклонил
    IN_WORK,              // Блогер работает
    SUBMITTED,            // Блогер сдал работу
    REVISION_REQUESTED,   // Бренд запросил доработку
    COMPLETED,            // Финально завершено ✅
    CANCELLED             // Отменено
}

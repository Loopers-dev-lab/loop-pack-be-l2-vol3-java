package com.loopers.domain.queue;

public record QueuePollingResult(
    QueueStatus status,
    QueuePosition position,
    EntryToken token
) {
    public static QueuePollingResult waiting(QueuePosition position) {
        return new QueuePollingResult(QueueStatus.WAITING, position, null);
    }

    public static QueuePollingResult tokenIssued(EntryToken token) {
        return new QueuePollingResult(QueueStatus.TOKEN_ISSUED, null, token);
    }

    public static QueuePollingResult tokenExpired() {
        return new QueuePollingResult(QueueStatus.TOKEN_EXPIRED, null, null);
    }

    public static QueuePollingResult notInQueue() {
        return new QueuePollingResult(QueueStatus.NOT_IN_QUEUE, null, null);
    }
}

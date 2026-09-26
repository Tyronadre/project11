package de.tyro.project11.attendance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttendanceMailQueue {
    private final AttendanceEmailRepository emails;
    private final Clock clock;

    public AttendanceMailQueue(AttendanceEmailRepository emails, Clock clock) {
        this.emails = emails; this.clock = clock;
    }

    // The caller holds the event lock; queue changes commit together with attendance.
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(long activityId, Set<Long> pendingMemberIds) {
        var existing = emails.findByActivityId(activityId);
        for (var email : existing) {
            if (!pendingMemberIds.contains(email.getUserId())) email.cancel();
            else email.requeue(clock.instant());
        }
        var known = existing.stream().map(AttendanceEmail::getUserId).collect(Collectors.toSet());
        for (var userId : pendingMemberIds) {
            if (!known.contains(userId)) emails.save(new AttendanceEmail(activityId, userId, clock.instant()));
        }
    }
}

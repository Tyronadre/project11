package de.tyro.project11.tallies;

import de.tyro.project11.attendance.AttendancePenaltyRepository;
import de.tyro.project11.calendar.CalendarTime;
import de.tyro.project11.registration.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TallyHistoryService {
    private final TallyRecordRepository records;
    private final AttendancePenaltyRepository penalties;
    private final Clock clock;
    public TallyHistoryService(TallyRecordRepository records, AttendancePenaltyRepository penalties, Clock clock) {
        this.records = records; this.penalties = penalties; this.clock = clock;
    }
    // Callers already hold the affected user's row lock. The count and journal commit together.
    @Transactional(propagation = Propagation.MANDATORY)
    public void change(AppUser user, int next, String actor, String reason, String source, Long sourceId) {
        int before = user.getTallyCount();
        if (before == next) return;
        records.save(new TallyRecord(user.getId(), clock.instant(), before, next, actor, reason, source, sourceId));
        user.setTallyCount(next);
    }
    public record Entry(String at, int before, int after, String actor, String reason, String url) {
        public String delta() { int n = after - before; return (n > 0 ? "+" : "") + n; }
    }
    @Transactional(readOnly = true)
    public Page<Entry> page(long userId, int page) {
        if (page < 0 || page > 100000) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        var format = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm", Locale.GERMAN).withZone(CalendarTime.BERLIN);
        return records.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, 20)).map(r -> new Entry(
                format.format(r.getCreatedAt()), r.getBeforeCount(), r.getAfterCount(), r.getActor(), r.getReason(),
                r.getSource().equals("EVENT") ? "/events/" + r.getSourceId()
                        : r.getSource().equals("TRAVEL") ? "/amt/reisen/" + r.getSourceId() : null));
    }
    @Transactional(readOnly = true)
    public Map<Long, String> eventMarks(long userId) {
        var result = new HashMap<Long, String>();
        penalties.findByUserId(userId).forEach(p -> result.put(p.getActivityId(), p.isApplied()
                ? "1 automatischer Strich für dieses Event vergeben" : "Automatischer Event-Strich zurückgenommen"));
        return result;
    }
}

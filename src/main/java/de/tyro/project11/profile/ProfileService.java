package de.tyro.project11.profile;

import de.tyro.project11.attendance.AttendanceRepository;
import de.tyro.project11.calendar.*;
import de.tyro.project11.portal.*;
import de.tyro.project11.registration.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProfileService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMAN);
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final BlogEntryRepository blogs;
    private final HolidayRepository holidays;
    private final ActivityRepository activities;
    private final AttendanceRepository attendance;
    private final TravelApplicationRepository travel;
    private final AbsenceApplicationRepository absences;
    private final TravelPhotoRepository photos;
    private final de.tyro.project11.attendance.AttendancePenaltyRepository penaltyRows;
    private final Clock clock;
    private final Validator validator;

    public ProfileService(UserRepository users, UserProfileRepository profiles, BlogEntryRepository blogs,
                          HolidayRepository holidays, ActivityRepository activities, AttendanceRepository attendance,
                          TravelApplicationRepository travel, Clock clock, Validator validator, AbsenceApplicationRepository absences, TravelPhotoRepository photos, de.tyro.project11.attendance.AttendancePenaltyRepository penaltyRows) {
        this.penaltyRows = penaltyRows;
        this.users = users; this.profiles = profiles; this.blogs = blogs; this.holidays = holidays;
        this.activities = activities; this.attendance = attendance; this.travel = travel;
        this.clock = clock; this.validator = validator; this.absences = absences; this.photos = photos;
    }
    public record Fact(String section, String question, String answer) {}
    public record Passage(String section, String question, String text) {}
    public record Photo(long id, String name) {}
    public record Story(long id, String title, String submitted, List<Passage> passages, List<Photo> photos) {}
    public record TimelineItem(long id, String kind, String title, String date, Instant sortAt, String description,
                               String status, String url, Long reportId, List<BlogBlock> blocks, String edited, Story report, Story absence, Story leave) {}
    // Deliberately excludes payment values from the regular page model.
    public record Profile(long id, String name, boolean owner, String color, String birthday, String joined,
                          boolean hasPayments, List<Fact> facts, List<TimelineItem> timeline) {}
    public record Payments(String name, String paypal, String iban) {}

    @Transactional(readOnly = true)
    public Profile load(long id, String email) {
        var viewer = currentUser(email);
        var member = member(id);
        var profile = profiles.findById(id).orElseGet(() -> new UserProfile(member));
        var facts = ProfileQuestions.ALL.stream().map(q -> new Fact(q.section(), q.label(),
                profile.getAnswers().getOrDefault(q.key(), "Noch nicht ausgefüllt"))).toList();
        List<TimelineItem> timeline = new ArrayList<>();
        var reports = travel.findByApplicantIdAndKind(id, TravelKind.REPORT).stream()
                .collect(Collectors.toMap(r -> r.getHoliday().getId(), r -> r));
        var leaves = travel.findByApplicantIdAndKind(id, TravelKind.LEAVE).stream()
                .collect(Collectors.toMap(r -> r.getHoliday().getId(), r -> r));
        for (var holiday : holidays.findByUserIdAndEndsOnBeforeOrderByEndsOnDesc(id, LocalDate.now(clock.withZone(CalendarTime.BERLIN)))) {
            timeline.add(new TimelineItem(holiday.getId(), "holiday", holiday.getTitle(),
                    holiday.getStartsOn().format(DAY) + " – " + holiday.getEndsOn().format(DAY),
                    holiday.getEndsOn().plusDays(1).atStartOfDay(CalendarTime.BERLIN).toInstant(),
                    holiday.getDescription(), "Urlaub", "/calendar?month=" + YearMonth.from(holiday.getStartsOn()) + "#holiday-" + holiday.getId(),
                    reports.containsKey(holiday.getId()) ? reports.get(holiday.getId()).getId() : null, List.of(), null,
                    travelStory(reports.get(holiday.getId())), null, travelStory(leaves.get(holiday.getId()))));
        }
        var sheets = attendance.findAllByOrderBySavedAtDesc().stream()
                .collect(Collectors.toMap(s -> s.getActivity().getId(), s -> s));
        var filedAbsences = absences.findByApplicantIdOrderBySubmittedAtDescIdDesc(id).stream()
                .collect(Collectors.toMap(a -> a.getActivity().getId(), a -> a));
        var eventItems = new LinkedHashMap<Long, Activity>();
        activities.findByEndsAtLessThanEqualOrderByEndsAtDescIdDesc(OffsetDateTime.now(clock)).forEach(e -> eventItems.put(e.getId(), e));
        filedAbsences.values().forEach(a -> eventItems.put(a.getActivity().getId(), a.getActivity()));
        var markedEvents = penaltyRows.findByUserId(id).stream().map(de.tyro.project11.attendance.AttendancePenalty::getActivityId).collect(Collectors.toSet());
        for (var event : eventItems.values()) {
            var absence = filedAbsences.get(event.getId());
            if (event.isCancelled() && absence == null && !markedEvents.contains(event.getId())) continue;
            var sheet = sheets.get(event.getId());
            String status;
            if (event.isCancelled()) status = "Event abgesagt";
            else if (event.getEndsAt().toInstant().isAfter(clock.instant())) status = "Event noch nicht beendet";
            else if (sheet == null) status = "Teilnahme noch nicht erfasst";
            else if (!contains(sheet.getRoster(), id)) status = "Nicht im damaligen Teilnehmerkreis";
            else status = contains(sheet.getAttendees(), id) ? "Teilgenommen" : "Nicht teilgenommen";
            // Membership is taken from the saved roster; no absent status is inferred from a missing sheet.
            if (absence == null && sheet == null && event.getEndsAt().isBefore(member.getCreatedAt())) continue;
            timeline.add(new TimelineItem(event.getId(), "event", event.getTitle(),
                    event.getStartsAt().atZoneSameInstant(CalendarTime.BERLIN).format(DAY), absence == null ? event.getEndsAt().toInstant() : absence.getSubmittedAt().toInstant(),
                    event.getDescription(), status, "/events/" + event.getId(), null, List.of(), null, null, absence == null ? null : new Story(absence.getId(), "Antrag auf Abwesenheit",
                            absence.getSubmittedAt().atZoneSameInstant(CalendarTime.BERLIN).format(DAY), passages(absence.getAnswers()), List.of()), null));
        }
        for (var blog : blogs.findByAuthorIdOrderByCreatedAtDescIdDesc(id)) {
            timeline.add(new TimelineItem(blog.getId(), "blog", blog.getTitle(),
                    blog.getCreatedAt().atZoneSameInstant(CalendarTime.BERLIN).format(DAY), blog.getCreatedAt().toInstant(),
                    "", "Blogbeitrag", null, null, blog.getBlocks(), blog.getUpdatedAt().equals(blog.getCreatedAt()) ? null
                    : blog.getUpdatedAt().atZoneSameInstant(CalendarTime.BERLIN).format(DAY), null, null, null));
        }
        timeline.sort(Comparator.comparing(TimelineItem::sortAt).reversed()
                .thenComparing(TimelineItem::kind).thenComparing(TimelineItem::id, Comparator.reverseOrder()));
        return new Profile(id, member.getDisplayName(), viewer.getId().equals(id), profile.getColor(),
                profile.getBirthday() == null ? null : profile.getBirthday().format(DAY),
                member.getCreatedAt().atZoneSameInstant(CalendarTime.BERLIN).format(DAY),
                !profile.getPaypal().isBlank() || !profile.getIban().isBlank(), facts, List.copyOf(timeline));
    }

    private List<Passage> passages(List<ApplicationAnswer> answers) {
        return answers.stream().map(a -> new Passage(a.getSection(), a.getQuestion(),
                a.getSection().equals("E. Bestätigungswesen") && a.getQuestion().startsWith("Allgemeine Gruppenbedingungen")
                        ? "Akzeptiert." : a.getAnswer())).toList();
    }
    private Story travelStory(TravelApplication application) {
        if (application == null) return null;
        var pictures = application.getKind() == TravelKind.REPORT ? photos.findByApplicationIdOrderByIdAsc(application.getId()).stream()
                .map(p -> new Photo(p.getId(), p.getOriginalName())).toList() : List.<Photo>of();
        return new Story(application.getId(), application.getSubject(), application.getSubmittedAt().atZoneSameInstant(CalendarTime.BERLIN).format(DAY),
                passages(application.getAnswers()), pictures);
    }

    @Transactional(readOnly = true)
    public long myId(String email) { return currentUser(email).getId(); }

    @Transactional(readOnly = true)
    public ProfileForm edit(long id, String email) {
        var member = requireOwner(id, email);
        var profile = profiles.findById(id).orElseGet(() -> new UserProfile(member));
        var form = new ProfileForm();
        form.setColor(profile.getColor()); form.setBirthday(profile.getBirthday());
        form.setPaypal(profile.getPaypal()); form.setIban(profile.getIban());
        form.setAnswers(new LinkedHashMap<>(profile.getAnswers()));
        return form;
    }

    @Transactional
    public void save(long id, String email, ProfileForm form) {
        requireOwner(id, email); validate(form);
        // Also serialize creation of the first profile for existing users.
        var member = users.findLockedById(id).orElseThrow(ProfileService::notFound);
        var profile = profiles.findById(id).orElseGet(() -> new UserProfile(member));
        profile.update(form); profiles.save(profile);
    }

    @Transactional(readOnly = true)
    public Payments payments(long id, String email) {
        currentUser(email);
        var member = member(id);
        var profile = profiles.findById(id).orElseGet(() -> new UserProfile(member));
        return new Payments(member.getDisplayName(), profile.getPaypal(), profile.getIban());
    }

    @Transactional(readOnly = true)
    public BlogForm editBlog(long id, long entryId, String email) {
        requireOwner(id, email);
        var entry = ownBlog(id, entryId);
        var form = new BlogForm(); form.setTitle(entry.getTitle());
        form.setBlocks(new ArrayList<>(entry.getBlocks()));
        return form;
    }

    @Transactional
    public long saveBlog(long id, Long entryId, String email, BlogForm form) {
        var author = requireOwner(id, email); validate(form);
        var now = OffsetDateTime.now(clock);
        var entry = entryId == null ? new BlogEntry(author, now) : ownBlog(id, entryId);
        entry.update(form, now);
        return blogs.save(entry).getId();
    }

    @Transactional
    public void deleteBlog(long id, long entryId, String email) {
        requireOwner(id, email); blogs.delete(ownBlog(id, entryId));
    }

    @Transactional(readOnly = true)
    public AppUser requireOwner(long id, String email) {
        var viewer = currentUser(email); member(id);
        if (!viewer.getId().equals(id)) throw new AccessDeniedException("Nur das eigene Profil darf bearbeitet werden.");
        return viewer;
    }
    private BlogEntry ownBlog(long id, long entryId) {
        var entry = blogs.findById(entryId).orElseThrow(ProfileService::notFound);
        if (!entry.getAuthor().getId().equals(id)) throw notFound();
        return entry;
    }
    private boolean contains(Set<AppUser> members, long id) { return members.stream().anyMatch(u -> u.getId().equals(id)); }
    private AppUser member(long id) { return users.findById(id).orElseThrow(ProfileService::notFound); }
    private AppUser currentUser(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
    }
    private void validate(Object form) {
        if (!validator.validate(form).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Eingaben.");
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}

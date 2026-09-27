package de.tyro.project11.portal;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Controller
@RequestMapping("/amt")
public class TravelController {
    private static final String DRAFTS = TravelController.class.getName() + ".drafts";
    private final PortalService portal;
    private final TravelService travel;
    private final TravelPhotoService photos;

    private final CaseFileService files;
    public TravelController(PortalService portal, TravelService travel, TravelPhotoService photos, CaseFileService files) {
        this.portal = portal; this.travel = travel; this.photos = photos; this.files = files;
    }

    @ModelAttribute("portalTerms")
    public PortalTerms.Document terms() { return PortalTerms.CURRENT; }

    @ModelAttribute("citizen")
    public PortalViews.Citizen citizen(Principal principal) { return portal.citizen(principal.getName()); }

    @InitBinder("travelForm")
    void bindForm(WebDataBinder binder) {
        var allowed = new ArrayList<>(List.of("values[holidayId]", "personalAnswers[0]", "personalAnswers[1]",
                "personalAnswers[2]", "knowledgeAnswer", "absurdAnswer", "loyaltyAnswer", "auditAnswer",
                "confirmedDetails", "confirmedConfirmation", "understoodConfirmation", "confirmedAll", "acceptedTerms"));
        Stream.concat(TravelFields.LEAVE.stream(), TravelFields.REPORT.stream())
                .map(field -> "values[" + field.key() + "]").forEach(allowed::add);
        binder.setAllowedFields(allowed.toArray(String[]::new));
        binder.setAutoGrowCollectionLimit(3);
    }

    @GetMapping("/{type:aab|eer}")
    public String form(@PathVariable String type, @RequestParam(required = false) String vorgang,
                       @RequestParam(required = false) Long holiday, HttpSession session, Principal principal, Model model) {
        if (vorgang == null) {
            photos.clearExpiredUploads();
            var draft = new TravelDraft(kind(type));
            draft.getForm().getValues().put("applicantName", ((PortalViews.Citizen) model.getAttribute("citizen")).name());
            if (holiday != null && draft.getKind() == TravelKind.REPORT) draft.getForm().getValues().put("holidayId", holiday.toString());
            synchronized (session) {
                var drafts = drafts(session);
                if (drafts.size() >= 8) drafts.remove(drafts.keySet().iterator().next());
                drafts.put(draft.getId(), draft);
            }
            if (draft.getKind() == TravelKind.REPORT) {
                model.addAttribute("travelForm", draft.getForm());
                formModel(draft, principal, model);
                return "portal/report-form";
            }
            return formRedirect(draft);
        }
        var draft = draft(session, vorgang, type);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            draft.edit();
            model.addAttribute("travelForm", draft.getForm());
            formModel(draft, principal, model);
            return draft.getKind() == TravelKind.REPORT ? "portal/report-form" : "portal/travel-form";
        }
    }

    @PostMapping("/{type:aab}/pruefen")
    public String prepare(@PathVariable String type, @RequestParam String vorgang,
                          @Valid @ModelAttribute("travelForm") TravelForm form, BindingResult errors,
                          @RequestParam(name = "photos", required = false) List<MultipartFile> uploads,
                          HttpSession session, Principal principal, Model model) {
        var draft = draft(session, vorgang, type);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            draft.update(form);
            travel.validate(draft, principal.getName(), errors);
            if (draft.getKind() == TravelKind.REPORT) photos.stage(draft, principal.getName(), uploads, errors);
            if (errors.hasErrors()) {
                formModel(draft, principal, model);
                return "portal/travel-form";
            }
            draft.review();
            return "redirect:/amt/" + type + "/pruefung?vorgang=" + draft.getId();
        }
    }

    @GetMapping("/{type:aab}/pruefung")
    public String review(@PathVariable String type, @RequestParam String vorgang, HttpSession session, Principal principal, Model model) {
        var draft = draft(session, vorgang, type);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            requireStage(draft, AbsenceDraft.Stage.REVIEW);
            model.addAttribute("draft", draft);
            model.addAttribute("sections", travel.preview(draft, principal.getName()));
            model.addAttribute("photos", photos.draftPhotos(draft.getId(), principal.getName()));
            return "portal/travel-review";
        }
    }

    @PostMapping("/{type:aab}/bestaetigen")
    public String confirm(@PathVariable String type, @RequestParam String vorgang, HttpSession session) {
        var draft = draft(session, vorgang, type);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            requireStage(draft, AbsenceDraft.Stage.REVIEW);
            draft.confirm();
            return "redirect:/amt/" + type + "/freigabe?vorgang=" + draft.getId();
        }
    }

    @GetMapping("/{type:aab}/freigabe")
    public String confirmation(@PathVariable String type, @RequestParam String vorgang, HttpSession session, Model model) {
        var draft = draft(session, vorgang, type);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            requireStage(draft, AbsenceDraft.Stage.FINAL);
            model.addAttribute("draft", draft);
            return "portal/travel-confirmation";
        }
    }

    @PostMapping("/{type:aab}/einreichen")
    public String submit(@PathVariable String type, @RequestParam String vorgang, HttpSession session,
                         Principal principal, RedirectAttributes redirect) {
        var draft = draft(session, vorgang, type);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            requireStage(draft, AbsenceDraft.Stage.FINAL);
            draft.submitted(travel.submit(draft, principal.getName()));
            redirect.addFlashAttribute("newlySubmitted", true);
            return filed(draft);
        }
    }

    @PostMapping("/eer/einreichen")
    public String submitReport(@RequestParam String vorgang,
                               @ModelAttribute("travelForm") TravelForm form, BindingResult errors,
                               @RequestParam(name = "photos", required = false) List<MultipartFile> uploads,
                               HttpSession session, Principal principal, Model model, RedirectAttributes redirect) {
        var draft = draft(session, vorgang, "eer");
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            draft.update(form);
            travel.validate(draft, principal.getName(), errors);
            if (!errors.hasErrors()) photos.stage(draft, principal.getName(), uploads, errors);
            if (errors.hasErrors()) {
                formModel(draft, principal, model);
                return "portal/report-form";
            }
            draft.submitted(travel.submit(draft, principal.getName()));
            redirect.addFlashAttribute("newlySubmitted", true);
            return filed(draft);
        }
    }

    @GetMapping("/reisen/{id}")
    public String file(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("file", travel.file(id, principal.getName()));
        model.addAttribute("dossier", files.travel(id, principal.getName()));
        return "portal/travel-application";
    }

    @GetMapping("/fotos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable long id, Principal principal) {
        var image = photos.read(id, principal.getName());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.type()))
                .cacheControl(CacheControl.noStore()).header("Content-Disposition", "inline; filename=\"foto-" + id
                        + (image.type().equals("image/png") ? ".png\"" : ".jpg\""))
                .body(image.content());
    }

    @PostMapping("/eer/fotos/{id}/entfernen")
    public String removePhoto(@PathVariable long id, @RequestParam String vorgang, HttpSession session, Principal principal) {
        var draft = draft(session, vorgang, "eer");
        synchronized (draft) {
            if (draft.getSubmittedId() != null) return filed(draft);
            photos.remove(draft.getId(), id, principal.getName());
            draft.edit();
            return formRedirect(draft);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String error(ResponseStatusException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("problem", exception.getReason());
        return "portal/problem";
    }

    private void formModel(TravelDraft draft, Principal principal, Model model) {
        model.addAttribute("draft", draft);
        model.addAttribute("fields", TravelFields.forKind(draft.getKind()));
        model.addAttribute("loyaltyOptions", PortalQuestions.LOYALTY_OPTIONS);
        var holidays = draft.getKind() == TravelKind.REPORT ? travel.reportableHolidays(principal.getName()) : List.<TravelService.HolidayOption>of();
        if (holidays.size() == 1 && draft.getForm().value("holidayId").isBlank()) {
            draft.getForm().getValues().put("holidayId", Long.toString(holidays.getFirst().id()));
        }
        model.addAttribute("holidays", holidays);
        model.addAttribute("photos", photos.draftPhotos(draft.getId(), principal.getName()));
    }

    private TravelKind kind(String type) { return type.equals("aab") ? TravelKind.LEAVE : TravelKind.REPORT; }
    private String filed(TravelDraft draft) { return "redirect:/amt/reisen/" + draft.getSubmittedId(); }
    private String formRedirect(TravelDraft draft) { return "redirect:/amt/" + draft.getKind().route + "?vorgang=" + draft.getId(); }
    private void requireStage(TravelDraft draft, AbsenceDraft.Stage stage) {
        if (draft.getStage() != stage) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Die vorgeschriebene Reihenfolge wurde verlassen. Bitte den Entwurf erneut aufrufen und prüfen.");
    }
    private TravelDraft draft(HttpSession session, String id, String type) {
        synchronized (session) {
            var draft = drafts(session).get(id);
            if (draft == null || draft.getKind() != kind(type)) throw new ResponseStatusException(HttpStatus.GONE,
                    "Dieser Entwurf ist nicht mehr in Ihrer Sitzung vorhanden. Bitte ein neues Verfahren beginnen.");
            return draft;
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String, TravelDraft> drafts(HttpSession session) {
        var value = session.getAttribute(DRAFTS);
        if (value == null) {
            value = new LinkedHashMap<String, TravelDraft>();
            session.setAttribute(DRAFTS, value);
        }
        return (Map<String, TravelDraft>) value;
    }
}

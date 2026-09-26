package de.tyro.project11.portal;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/amt")
public class PortalController {
    private static final String DRAFTS = PortalController.class.getName() + ".drafts";
    private final PortalService portal;

    private final CaseFileService files;
    public PortalController(PortalService portal, CaseFileService files) { this.portal = portal; this.files = files; }

    @ModelAttribute("portalTerms")
    public PortalTerms.Document terms() { return PortalTerms.CURRENT; }

    @ModelAttribute("citizen")
    public PortalViews.Citizen citizen(Principal principal) { return portal.citizen(principal.getName()); }

    @InitBinder("absenceForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("applicantName", "activityId", "activityDate", "destination",
                "companions", "reason", "priorityReason", "detailedReason", "transport", "catering",
                "personalAnswers[0]", "personalAnswers[1]", "personalAnswers[2]", "knowledgeAnswer", "absurdAnswer",
                "loyaltyAnswer", "auditAnswer", "confirmedDetails", "confirmedConfirmation", "understoodConfirmation", "confirmedAll", "acceptedTerms");
        binder.setAutoGrowCollectionLimit(3);
    }

    @GetMapping({"", "/"})
    public String index(Model model) {
        var archive = portal.archive(null, 0);
        model.addAttribute("recent", archive.getContent().stream().limit(5).toList());
        model.addAttribute("totalApplications", archive.getTotalElements());
        model.addAttribute("members", portal.members());
        return "portal/index";
    }

    @GetMapping("/akten")
    public String archive(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("archive", portal.archive(null, page));
        model.addAttribute("members", portal.members());
        return "portal/archive";
    }

    @GetMapping("/mitglieder/{id}")
    public String member(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("member", portal.member(id));
        model.addAttribute("archive", portal.archive(id, page));
        model.addAttribute("members", portal.members());
        return "portal/archive";
    }

    @GetMapping("/antraege/{id}")
    public String application(@PathVariable long id, Principal principal, Model model) {
        model.addAttribute("file", portal.file(id));
        model.addAttribute("dossier", files.absence(id, principal.getName()));
        return "portal/application";
    }

    @GetMapping("/aaa")
    public String form(@RequestParam(required = false) String vorgang, @RequestParam(required = false) Long activity, HttpSession session,
                       Principal principal, Model model) {
        if (vorgang == null) {
            var draft = new AbsenceDraft();
            if (activity != null && portal.availableActivities(principal.getName()).stream().anyMatch(option -> option.id() == activity)) {
                draft.getForm().setActivityId(activity);
            }
            draft.getForm().setApplicantName(((PortalViews.Citizen) model.getAttribute("citizen")).name());
            synchronized (session) {
                var drafts = drafts(session);
                if (drafts.size() >= 8) {
                    drafts.remove(drafts.keySet().iterator().next());
                }
                drafts.put(draft.getId(), draft);
            }
            return "redirect:/amt/aaa?vorgang=" + draft.getId();
        }
        var draft = draft(session, vorgang);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) { return filed(draft); }
            draft.edit();
            model.addAttribute("absenceForm", draft.getForm());
            formModel(draft, principal, model);
            return "portal/absence-form";
        }
    }

    @PostMapping("/aaa/pruefen")
    public String prepare(@RequestParam String vorgang, @Valid @ModelAttribute("absenceForm") AbsenceForm form,
                          BindingResult errors, HttpSession session, Principal principal, Model model) {
        var draft = draft(session, vorgang);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) { return filed(draft); }
            draft.update(form);
            portal.validate(form, draft.getQuestions(), principal.getName(), errors);
            if (errors.hasErrors()) {
                formModel(draft, principal, model);
                return "portal/absence-form";
            }
            draft.review();
            return "redirect:/amt/aaa/pruefung?vorgang=" + draft.getId();
        }
    }

    @GetMapping("/aaa/pruefung")
    public String review(@RequestParam String vorgang, HttpSession session, Model model) {
        var draft = draft(session, vorgang);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) { return filed(draft); }
            requireStage(draft, AbsenceDraft.Stage.REVIEW);
            model.addAttribute("draft", draft);
            model.addAttribute("sections", portal.preview(draft.getForm(), draft.getQuestions()));
            return "portal/review";
        }
    }

    @PostMapping("/aaa/bestaetigen")
    public String confirm(@RequestParam String vorgang, HttpSession session) {
        var draft = draft(session, vorgang);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) { return filed(draft); }
            requireStage(draft, AbsenceDraft.Stage.REVIEW);
            draft.confirm();
            return "redirect:/amt/aaa/freigabe?vorgang=" + draft.getId();
        }
    }

    @GetMapping("/aaa/freigabe")
    public String finalConfirmation(@RequestParam String vorgang, HttpSession session, Model model) {
        var draft = draft(session, vorgang);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) { return filed(draft); }
            requireStage(draft, AbsenceDraft.Stage.FINAL);
            model.addAttribute("draft", draft);
            return "portal/confirmation";
        }
    }

    @PostMapping("/aaa/einreichen")
    public String submit(@RequestParam String vorgang, HttpSession session, Principal principal, RedirectAttributes redirect) {
        var draft = draft(session, vorgang);
        synchronized (draft) {
            if (draft.getSubmittedId() != null) { return filed(draft); }
            requireStage(draft, AbsenceDraft.Stage.FINAL);
            draft.submitted(portal.submit(principal.getName(), draft.getForm(), draft.getQuestions()));
            redirect.addFlashAttribute("newlySubmitted", true);
            return filed(draft);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String error(ResponseStatusException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.getStatusCode().value());
        model.addAttribute("problem", exception.getReason());
        return "portal/problem";
    }

    private void formModel(AbsenceDraft draft, Principal principal, Model model) {
        model.addAttribute("draft", draft);
        model.addAttribute("activities", portal.availableActivities(principal.getName()));
        model.addAttribute("loyaltyOptions", PortalQuestions.LOYALTY_OPTIONS);
    }

    private String filed(AbsenceDraft draft) { return "redirect:/amt/antraege/" + draft.getSubmittedId(); }

    private void requireStage(AbsenceDraft draft, AbsenceDraft.Stage stage) {
        if (draft.getStage() != stage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die vorgeschriebene Reihenfolge wurde verlassen. Bitte den Entwurf erneut aufrufen und prüfen.");
        }
    }

    private AbsenceDraft draft(HttpSession session, String id) {
        synchronized (session) {
            var draft = drafts(session).get(id);
            if (draft == null) {
                throw new ResponseStatusException(HttpStatus.GONE, "Dieser Entwurf ist nicht mehr in Ihrer Sitzung vorhanden. Bitte einen neuen Antrag beginnen.");
            }
            return draft;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, AbsenceDraft> drafts(HttpSession session) {
        var value = session.getAttribute(DRAFTS);
        if (value == null) {
            value = new LinkedHashMap<String, AbsenceDraft>();
            session.setAttribute(DRAFTS, value);
        }
        return (Map<String, AbsenceDraft>) value;
    }
}

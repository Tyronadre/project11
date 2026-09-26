package de.tyro.project11.portal;

import de.tyro.project11.registration.AppUser;
import de.tyro.project11.registration.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TravelPhotoService {
    public static final long MAX_PHOTO_BYTES = 4 * 1024 * 1024 * 1024L;
    private static final long MAX_TOTAL_BYTES = 12 * 1024 * 1024 * 1024L;
    private final TravelPhotoRepository photos;
    private final UserRepository users;
    private final Clock clock;
    public TravelPhotoService(TravelPhotoRepository photos, UserRepository users, Clock clock) {
        this.photos = photos; this.users = users; this.clock = clock;
    }

    @Transactional
    public void clearExpiredUploads() { photos.removeExpiredDrafts(OffsetDateTime.now(clock).minusDays(1)); }

    @Transactional(readOnly = true)
    public List<TravelPhotoRepository.Info> draftPhotos(String draftId, String email) {
        return photos.findByDraftKeyAndOwnerIdAndApplicationIsNullOrderByIdAsc(draftId, user(email).getId());
    }

    @Transactional
    public void stage(TravelDraft draft, String email, List<MultipartFile> files, BindingResult errors) {
        var owner = user(email);
        var existing = photos.findByDraftKeyAndOwnerIdAndApplicationIsNullOrderByIdAsc(draft.getId(), owner.getId());
        var uploads = files == null ? List.<MultipartFile>of() : files.stream().filter(file -> !file.isEmpty()).toList();
        if (existing.size() + uploads.size() > 6) {
            errors.reject("photos", "Höchstens sechs Fotos pro Bericht. Entfernen Sie bei Bedarf ein Entwurfsfoto.");
            return;
        }
        List<TravelPhoto> prepared = new ArrayList<>();
        long bytes = existing.stream().mapToLong(TravelPhotoRepository.Info::getSizeBytes).sum();
        for (var upload : uploads) {
            try {
                var picture = normalize(upload);
                bytes += picture.content().length;
                if (bytes > MAX_TOTAL_BYTES) {
                    errors.reject("photos", "Die Fotos dürfen zusammen höchstens 1 GiB umfassen. Bitte kleinere Bilder auswählen.");
                    return;
                }
                String name = upload.getOriginalFilename() == null ? "Reisefoto" : upload.getOriginalFilename();
                name = name.replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
                if (name.isBlank()) name = "Reisefoto";
                if (name.length() > 200) name = name.substring(0, 200);
                prepared.add(new TravelPhoto(owner, draft.getId(), name, picture.type(), picture.content(), OffsetDateTime.now(clock)));
            } catch (IOException | IllegalArgumentException exception) {
                errors.reject("photos", "Ein Foto ist ungültig: nur lesbare JPEG-/PNG-Bilder bis 4 MiB und 20 Megapixel sind erlaubt. Bitte erneut auswählen.");
                return;
            }
        }
        photos.saveAll(prepared);
    }

    @Transactional
    public void remove(String draftId, long photoId, String email) {
        var photo = photos.findById(photoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (photo.getApplication() != null || !photo.getDraftKey().equals(draftId) || !photo.getOwner().getId().equals(user(email).getId())) {
            throw new AccessDeniedException("Dieses Foto gehört nicht zu Ihrem bearbeitbaren Entwurf.");
        }
        photos.delete(photo);
    }

    public record Image(String type, byte[] content) {}

    @Transactional(readOnly = true)
    public Image read(long id, String email) {
        var photo = photos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (photo.getApplication() == null && !photo.getOwner().getId().equals(user(email).getId())) {
            throw new AccessDeniedException("Entwurfsfotos sind nur für die einreichende Person sichtbar.");
        }
        return new Image(photo.getContentType(), photo.getContent());
    }

    private Image normalize(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_PHOTO_BYTES) throw new IllegalArgumentException();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException();
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw new IllegalArgumentException();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 20_000_000) throw new IllegalArgumentException();
                var image = reader.read(0);
                try (var output = new ByteArrayOutputStream()) {
                    // Re-encode pixels: no original metadata or trailing uploaded content is served.
                    if (!ImageIO.write(image, format, output)) throw new IllegalArgumentException();
                    if (output.size() > MAX_PHOTO_BYTES) throw new IllegalArgumentException();
                    return new Image("image/" + format, output.toByteArray());
                } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }

    private AppUser user(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow(() -> new AccessDeniedException("Konto nicht gefunden."));
    }
}

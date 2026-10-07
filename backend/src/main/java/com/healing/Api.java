package com.healing;

import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class Api {
  public static void main(String[] a) { SpringApplication.run(Api.class, a); }
}

record Creds(String name, String email, String password) {}
record Role(String role, Long therapistId) {}
record Step(String title, String detail) {}

@RestController @RequestMapping("/api")
class Routes {
  private final UserRepo users; private final MoodRepo moods; private final JournalRepo journals;
  private final AssignRepo assigns; private final SafetyRepo safety; private final JdbcTemplate jdbc;
  private final PasswordEncoder enc; private final Jwt jwt;
  Routes(UserRepo u, MoodRepo m, JournalRepo j, AssignRepo a, SafetyRepo s, JdbcTemplate d, PasswordEncoder e, Jwt t) {
    users = u; moods = m; journals = j; assigns = a; safety = s; jdbc = d; enc = e; jwt = t;
  }
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<String> bad(IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }

  static Map<String, Object> view(AppUser u) {
    return Map.of("id", u.id, "name", u.name, "email", u.email, "role", u.role,
      "therapistId", u.therapistId == null ? 0L : u.therapistId, "shareConsent", u.shareConsent);
  }
  <T> T found(Optional<T> o) { return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
  AppUser create(Creds c, String role) {
    if (c.email() == null || !c.email().contains("@") || c.password() == null || c.password().length() < 8
        || c.name() == null || c.name().isBlank()) throw new IllegalArgumentException("Name, valid email and 8+ char password required");
    String email = c.email().trim().toLowerCase();
    if (users.findByEmail(email) != null) throw new IllegalArgumentException("Email already registered");
    AppUser u = new AppUser(); u.name = c.name().trim(); u.email = email; u.role = role; u.passwordHash = enc.encode(c.password());
    return users.save(u);
  }
  AppUser client(Long tid, Long cid) {
    AppUser c = found(users.findById(cid));
    if (!tid.equals(c.therapistId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return c;
  }

  // ---- auth (public registration always creates plain USER accounts)
  @PostMapping("/auth/register") Map<String, Object> register(@RequestBody Creds c) {
    AppUser u = create(c, "USER"); return Map.of("token", jwt.make(u), "user", view(u));
  }
  @PostMapping("/auth/login") Map<String, Object> login(@RequestBody Creds c) {
    AppUser u = c.email() == null ? null : users.findByEmail(c.email().trim().toLowerCase());
    if (u == null || c.password() == null || !enc.matches(c.password(), u.passwordHash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password");
    return Map.of("token", jwt.make(u), "user", view(u));
  }

  // ---- any signed-in user (own data only)
  @GetMapping("/me") Map<String, Object> me(@AuthenticationPrincipal Long uid) { return view(found(users.findById(uid))); }
  @PutMapping("/me/consent") Map<String, Object> consent(@AuthenticationPrincipal Long uid, @RequestBody Map<String, Boolean> b) {
    AppUser u = found(users.findById(uid)); u.shareConsent = Boolean.TRUE.equals(b.get("share")); return view(users.save(u));
  }
  @GetMapping("/moods") List<Mood> moods(@AuthenticationPrincipal Long uid) { return moods.findTop30ByUserIdOrderByIdDesc(uid); }
  @PostMapping("/moods") Mood addMood(@AuthenticationPrincipal Long uid, @RequestBody Mood m) {
    if (m.mood < 1 || m.mood > 5) throw new IllegalArgumentException("mood must be 1-5");
    m.id = null; m.userId = uid; return moods.save(m);
  }
  @GetMapping("/journal") List<Journal> journal(@AuthenticationPrincipal Long uid) { return journals.findTop30ByUserIdOrderByIdDesc(uid); }
  @PostMapping("/journal") Journal addJournal(@AuthenticationPrincipal Long uid, @RequestBody Journal j) {
    if (j.content == null || j.content.isBlank()) throw new IllegalArgumentException("Entry is empty");
    j.id = null; j.userId = uid; return journals.save(j);
  }
  @DeleteMapping("/journal/{id}") void delJournal(@AuthenticationPrincipal Long uid, @PathVariable Long id) {
    Journal j = found(journals.findById(id));
    if (!uid.equals(j.userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    journals.delete(j);
  }
  @GetMapping("/assignments") List<Assignment> myAssigns(@AuthenticationPrincipal Long uid) { return assigns.findByUserIdOrderByIdDesc(uid); }
  @PutMapping("/assignments/{id}/done") Assignment done(@AuthenticationPrincipal Long uid, @PathVariable Long id) {
    Assignment a = found(assigns.findById(id));
    if (!uid.equals(a.userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    a.done = !a.done; return assigns.save(a);
  }
  @GetMapping("/safety-plan") SafetyPlan plan(@AuthenticationPrincipal Long uid) {
    return safety.findById(uid).orElseGet(() -> { SafetyPlan p = new SafetyPlan(); p.userId = uid; return p; });
  }
  @PutMapping("/safety-plan") SafetyPlan savePlan(@AuthenticationPrincipal Long uid, @RequestBody SafetyPlan p) { p.userId = uid; return safety.save(p); }

  // ---- therapist: only clients assigned by admin; moods visible only with the client's consent
  @GetMapping("/therapist/clients") List<Map<String, Object>> clients(@AuthenticationPrincipal Long tid) {
    return users.findByTherapistId(tid).stream().map(Routes::view).toList();
  }
  @GetMapping("/therapist/clients/{id}") Map<String, Object> clientDetail(@AuthenticationPrincipal Long tid, @PathVariable Long id) {
    AppUser c = client(tid, id);
    return Map.of("client", view(c), "assignments", assigns.findByUserIdOrderByIdDesc(id),
      "moods", c.shareConsent ? moods.findTop30ByUserIdOrderByIdDesc(id) : List.of());
  }
  @PostMapping("/therapist/clients/{id}/assignments") Assignment assign(@AuthenticationPrincipal Long tid, @PathVariable Long id, @RequestBody Step s) {
    client(tid, id);
    if (s.title() == null || s.title().isBlank()) throw new IllegalArgumentException("Title required");
    Assignment a = new Assignment(); a.therapistId = tid; a.userId = id; a.title = s.title(); a.detail = s.detail(); return assigns.save(a);
  }

  // ---- admin: manages accounts and sees only anonymous aggregates, never journals or individual moods
  @GetMapping("/admin/users") List<Map<String, Object>> allUsers() { return users.findAll().stream().map(Routes::view).toList(); }
  @GetMapping("/admin/stats") Map<String, Object> stats() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("users", jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role='USER'", Long.class));
    m.put("therapists", jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role='THERAPIST'", Long.class));
    m.put("checkinsLast7Days", jdbc.queryForObject("SELECT COUNT(*) FROM mood_entry WHERE created_at > DATEADD('DAY',-7,CURRENT_TIMESTAMP)", Long.class));
    m.put("avgMoodLast7Days", jdbc.queryForObject("SELECT COALESCE(ROUND(AVG(mood),2),0) FROM mood_entry WHERE created_at > DATEADD('DAY',-7,CURRENT_TIMESTAMP)", Double.class));
    return m;
  }
  @PostMapping("/admin/therapists") Map<String, Object> addTherapist(@RequestBody Creds c) { return view(create(c, "THERAPIST")); }
  @PutMapping("/admin/users/{id}") Map<String, Object> update(@AuthenticationPrincipal Long me, @PathVariable Long id, @RequestBody Role r) {
    if (!List.of("USER", "THERAPIST", "ADMIN").contains(r.role())) throw new IllegalArgumentException("Bad role");
    if (me.equals(id) && !"ADMIN".equals(r.role())) throw new IllegalArgumentException("You cannot demote yourself");
    AppUser u = found(users.findById(id)); u.role = r.role();
    u.therapistId = (r.therapistId() == null || r.therapistId() == 0) ? null : r.therapistId();
    if (u.therapistId != null && !"THERAPIST".equals(found(users.findById(u.therapistId)).role))
      throw new IllegalArgumentException("Assigned therapist must have the THERAPIST role");
    return view(users.save(u));
  }
  @DeleteMapping("/admin/users/{id}") @Transactional void deleteUser(@AuthenticationPrincipal Long me, @PathVariable Long id) {
    if (me.equals(id)) throw new IllegalArgumentException("You cannot delete yourself");
    for (String t : List.of("mood_entry", "journal_entry", "assignment", "safety_plan")) jdbc.update("DELETE FROM " + t + " WHERE user_id=?", id);
    jdbc.update("UPDATE app_user SET therapist_id=NULL WHERE therapist_id=?", id);
    users.deleteById(id);
  }
}

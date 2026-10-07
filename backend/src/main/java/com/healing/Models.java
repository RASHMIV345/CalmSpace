package com.healing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity @Table(name = "app_user")
class AppUser {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String name, email, passwordHash, role;
  public Long therapistId;
  public boolean shareConsent;
}
@Entity @Table(name = "mood_entry")
class Mood {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public Long userId; public int mood; public String note;
  @Column(insertable = false, updatable = false) public LocalDateTime createdAt;
}
@Entity @Table(name = "journal_entry")
class Journal {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public Long userId; public String prompt, content;
  @Column(insertable = false, updatable = false) public LocalDateTime createdAt;
}
@Entity @Table(name = "assignment")
class Assignment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public Long therapistId, userId; public String title, detail; public boolean done;
  @Column(insertable = false, updatable = false) public LocalDateTime createdAt;
}
@Entity @Table(name = "safety_plan")
class SafetyPlan {
  @Id public Long userId;
  public String warningSigns, coping, people, reasons;
}
interface UserRepo extends JpaRepository<AppUser, Long> { AppUser findByEmail(String e); List<AppUser> findByTherapistId(Long t); }
interface MoodRepo extends JpaRepository<Mood, Long> { List<Mood> findTop30ByUserIdOrderByIdDesc(Long u); }
interface JournalRepo extends JpaRepository<Journal, Long> { List<Journal> findTop30ByUserIdOrderByIdDesc(Long u); }
interface AssignRepo extends JpaRepository<Assignment, Long> { List<Assignment> findByUserIdOrderByIdDesc(Long u); }
interface SafetyRepo extends JpaRepository<SafetyPlan, Long> {}

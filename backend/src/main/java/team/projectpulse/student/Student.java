package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.system.WeekKeys;
import team.projectpulse.team.Team;
import team.projectpulse.user.PeerEvaluationUser;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class Student extends PeerEvaluationUser {

    @ManyToOne(optional = false)
    private Section section;
    @ManyToOne
    private Team team;
    /**
     * The calendar date the student was deactivated, or {@code null} while she is active. Invariant: for a student,
     * {@code enabled} is false exactly when this is set, which is why {@link #deactivate(LocalDate)} and
     * {@link #reactivate()} are the only writers of either.
     */
    private LocalDate deactivatedOn;


    public Student() {
    }

    public Student(String username, String firstName, String lastName, String email, String password, boolean enabled, String roles) {
        super(username, firstName, lastName, email, password, enabled, roles);
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public LocalDate getDeactivatedOn() {
        return deactivatedOn;
    }

    /**
     * Deactivates the student as of the given calendar date (UC-STU-deactivate-student). She stays on her team and
     * her work is kept (BR-student-lifecycle).
     *
     * @param date today, read from the injected {@code Clock}
     */
    public void deactivate(LocalDate date) {
        setEnabled(false);
        this.deactivatedOn = date;
    }

    /**
     * Reactivates the student and forgets her deactivation date.
     */
    public void reactivate() {
        setEnabled(true);
        this.deactivatedOn = null;
    }

    /**
     * Whether teammates may evaluate this student for the given week (BR-deactivated-student-evaluation): always
     * while she is active, and once deactivated only for weeks that ended before the date of her deactivation.
     * Week keys are ISO {@code YYYY-Www}, so they order as strings.
     *
     * @param week a week key such as {@code "2023-W32"}
     */
    public boolean isEvaluableFor(String week) {
        if (deactivatedOn == null) {
            return isEnabled();
        }
        return week.compareTo(WeekKeys.of(deactivatedOn)) < 0;
    }

}

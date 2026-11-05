package team.projectpulse.student;

import team.projectpulse.team.Team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Student lifecycle and evaluability (UC-STU-deactivate-student, BR-deactivated-student-evaluation)")
class StudentTest {

    private static Student activeStudent() {
        return new Student("nora", "Nora", "Quinn", "n.quinn@abc.edu", "123456", true, "student");
    }

    @Test
    void testDeactivateRecordsTheDateAndKeepsTheTeam() {
        Student student = activeStudent();
        Team team = new Team("Team3", "", "");
        student.setTeam(team);

        student.deactivate(LocalDate.of(2023, 8, 16));

        assertThat(student.isEnabled()).isFalse();
        assertThat(student.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 16));
        assertThat(student.getTeam()).isSameAs(team);
    }

    @Test
    void testReactivateClearsTheDate() {
        Student student = activeStudent();
        student.deactivate(LocalDate.of(2023, 8, 16));

        student.reactivate();

        assertThat(student.isEnabled()).isTrue();
        assertThat(student.getDeactivatedOn()).isNull();
    }

    @Test
    void testActiveStudentIsEvaluableForEveryWeek() {
        Student student = activeStudent();

        assertThat(student.isEvaluableFor("2023-W32")).isTrue();
        assertThat(student.isEvaluableFor("2023-W40")).isTrue();
    }

    @Test
    void testDeactivatedStudentIsEvaluableOnlyForWeeksBeforeHerDeactivation() {
        Student student = activeStudent();
        student.deactivate(LocalDate.of(2023, 8, 16)); // a Wednesday in 2023-W33

        assertThat(student.isEvaluableFor("2023-W32")).isTrue();
        assertThat(student.isEvaluableFor("2023-W33")).isFalse();
        assertThat(student.isEvaluableFor("2023-W34")).isFalse();
    }

    @Test
    void testDeactivationOnMondayExcludesThatWholeWeek() {
        Student student = activeStudent();
        student.deactivate(LocalDate.of(2023, 8, 14)); // the Monday of 2023-W33

        assertThat(student.isEvaluableFor("2023-W32")).isTrue();
        assertThat(student.isEvaluableFor("2023-W33")).isFalse();
    }

    @Test
    void testWeekKeysCompareAcrossAYearBoundary() {
        Student student = activeStudent();
        student.deactivate(LocalDate.of(2024, 1, 3)); // 2024-W01

        assertThat(student.isEvaluableFor("2023-W52")).isTrue();
        assertThat(student.isEvaluableFor("2024-W01")).isFalse();
    }

}

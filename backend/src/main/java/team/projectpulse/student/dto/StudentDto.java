package team.projectpulse.student.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;

/**
 * @param deactivatedOn the date the student was deactivated, or {@code null} while she is active, and always
 *                      {@code null} on the teammate list
 * @param evaluable     on the teammate list only: whether she may be evaluated for the week the peer evaluation form
 *                      rates (BR-deactivated-student-evaluation); {@code null} everywhere else
 */

public record StudentDto(Integer id,
                         @NotEmpty(message = "username is required.")
                         String username,
                         @NotEmpty(message = "first rubricName is required.")
                         String firstName,
                         @NotEmpty(message = "last rubricName is required.")
                         String lastName,
                         @NotEmpty(message = "email is required.")
                         String email,
                         boolean enabled,
                         String roles,
                         Integer sectionId,
                         String sectionName,
                         Integer teamId,
                         String teamName,
                         LocalDate deactivatedOn,
                         Boolean evaluable) {

}

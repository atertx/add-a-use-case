package team.projectpulse.system.exception;

/**
 * The student named by a deactivation is already deactivated (UC-STU-deactivate-student, extension 3b).
 * {@code ExceptionHandlerAdvice} answers {@code 409 CONFLICT} with this message.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("This student is already deactivated.");
    }

}

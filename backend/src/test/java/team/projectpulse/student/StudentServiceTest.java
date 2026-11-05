package team.projectpulse.student;

import org.junit.jupiter.api.AfterEach;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.exception.StudentAlreadyDeactivatedException;
import team.projectpulse.team.Team;
import team.projectpulse.user.userinvitation.UserInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    SectionRepository sectionRepository;
    @Mock
    UserInvitationService userInvitationService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    UserUtils userUtils;
    @Mock
    Clock clock;

    @InjectMocks
    private StudentService studentService;

    List<Student> students;

    @BeforeEach
    void setUp() {
        // Create students
        Student john = new Student("john", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        Student eric = new Student("eric", "Eric", "Hudson", "e.hudson@abc.edu", "123456", true, "student");
        Student jerry = new Student("jerry", "Jerry", "Moon", "j.moon@abc.edu", "123456", true, "student");
        Student woody = new Student("woody", "Woody", "Allen", "w.allen@abc.edu", "123456", true, "student");
        Student amanda = new Student("amanda", "Amanda", "Wagner", "a.wagner@abc.edu", "123456", true, "student");
        Student cora = new Student("cora", "Cora", "Manning", "c.manning@abc.edu", "123456", true, "student");
        Student agustin = new Student("agustin", "Agustin", "Freeman", "a.freeman@abc.edu", "123456", true, "student");
        Student mavis = new Student("mavis", "Mavis", "Huber", "m.huber@abc.edu", "123456", true, "student");
        Student mary = new Student("mary", "Mary", "Vargas", "m.vargas@abc.edu", "123456", true, "student");
        Student rosendo = new Student("rosendo", "Rosendo", "Maxwell", "r.maxwell@abc.edu", "123456", true, "student");

        this.students = List.of(john, eric, jerry, woody, amanda, cora, agustin, mavis, mary, rosendo);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext(); // Clear the security context after each test to avoid interference between tests
    }

    @Test
    void testFindByCriteria() {
        // Given
        Map<String, String> searchCriteria = Map.of("firstName", "john");
        PageImpl expectedPage = new PageImpl(List.of(this.students.get(0)));

        given(this.userUtils.getUserSectionId()).willReturn(2);
        given(this.studentRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(expectedPage);
        // When
        Page<Student> result = this.studentService.findByCriteria(searchCriteria, PageRequest.of(0, 20));

        // Then
        assertThat(result.getContent().size()).isEqualTo(1);
    }

    @Test
    void testFindStudentById() {
        // Given
        Student john = this.students.get(0);
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));

        // When
        Student result = this.studentService.findStudentById(1);

        // Then
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Smith");
    }

    @Test
    void testSaveStudent() {
        // Given
        Student jan = new Student("jan", "Jan", "Mckinney", "j.mckinney@abc.edu", "Abc123456", true, "student");
        doNothing().when(this.userInvitationService).validateUserInvitation(anyString(), anyString(), anyInt(), anyInt(), anyString());
        given(this.sectionRepository.findById(1)).willReturn(Optional.of(new Section()));
        given(this.passwordEncoder.encode(anyString())).willReturn("Encoded Password");
        given(this.studentRepository.save(jan)).willReturn(jan);

        // When
        Student result = this.studentService.saveStudent(jan, 1, 1, "token", "student");

        // Then
        assertThat(result.getFirstName()).isEqualTo("Jan");
        assertThat(result.getLastName()).isEqualTo("Mckinney");
        assertThat(result.getEmail()).isEqualTo("j.mckinney@abc.edu");
        assertThat(result.getRoles()).isEqualTo("student");
        assertThat(result.getSection()).isNotNull();
        assertThat(result.isEnabled()).isEqualTo(true);
    }

    @Test
    void testStudentUpdateOwnInfo() {
        // Given
        Student update = new Student("john", "John (updated)", "Smith (updated)", "j.smith@abc.edu (updated)", "", false, "admin student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        given(this.studentRepository.save(any(Student.class))).willReturn(this.students.get(0));

        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(false); // the caller is the student herself

        // When
        Student result = this.studentService.updateStudent(1, update);

        // Then
        assertThat(result.getUsername()).isEqualTo(update.getUsername());
        assertThat(result.getFirstName()).isEqualTo(update.getFirstName());
        assertThat(result.getLastName()).isEqualTo(update.getLastName());
        assertThat(result.getEmail()).isEqualTo(update.getEmail());
        assertThat(result.getRoles()).isNotEqualTo(update.getRoles());
        assertThat(result.isEnabled()).isNotEqualTo(update.isEnabled());
    }

    @Test
    void testInstructorUpdatesStudent() {
        // Given: the instructor edits the name and keeps the status as it is
        Student update = new Student("john", "John (updated)", "Smith (updated)", "j.smith@abc.edu (updated)", "", true, "admin student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        given(this.studentRepository.save(any(Student.class))).willReturn(this.students.get(0));
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);

        // When
        Student result = this.studentService.updateStudent(1, update);

        // Then
        assertThat(result.getUsername()).isEqualTo(update.getUsername());
        assertThat(result.getFirstName()).isEqualTo(update.getFirstName());
        assertThat(result.getLastName()).isEqualTo(update.getLastName());
        assertThat(result.getEmail()).isEqualTo(update.getEmail());
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getDeactivatedOn()).isNull();
    }

    @Test
    void testInstructorCannotDeactivateThroughUpdate() {
        // Given: interim rule, deactivation goes through deactivateStudent so that the date is recorded
        Student update = new Student("john", "John", "Smith", "j.smith@abc.edu", "", false, "student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);

        // When
        Throwable thrown = catchThrowable(() -> this.studentService.updateStudent(1, update));

        // Then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessage("Use Deactivate to deactivate a student.");
        assertThat(this.students.get(0).isEnabled()).isTrue();
        verify(this.studentRepository, never()).save(any(Student.class));
    }

    @Test
    void testInstructorReEnablesDeactivatedStudentThroughUpdate() {
        // Given
        Student deactivated = this.students.get(0);
        deactivated.deactivate(LocalDate.of(2023, 8, 16));
        Student update = new Student("john", "John", "Smith", "j.smith@abc.edu", "", true, "student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(deactivated));
        given(this.studentRepository.save(any(Student.class))).willReturn(deactivated);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);

        // When
        Student result = this.studentService.updateStudent(1, update);

        // Then
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getDeactivatedOn()).isNull();
    }

    @Test
    void testInstructorRenamesDeactivatedStudentWithoutChangingStatus() {
        // Given: the form sends the stored status (false) back unchanged
        Student deactivated = this.students.get(0);
        deactivated.deactivate(LocalDate.of(2023, 8, 16));
        Student update = new Student("john", "Johnny", "Smith", "j.smith@abc.edu", "", false, "student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(deactivated));
        given(this.studentRepository.save(any(Student.class))).willReturn(deactivated);
        given(this.userUtils.hasRole("ROLE_instructor")).willReturn(true);

        // When
        Student result = this.studentService.updateStudent(1, update);

        // Then
        assertThat(result.getFirstName()).isEqualTo("Johnny");
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 16));
    }

    @Test
    void testDeactivateStudentRecordsTodayAndKeepsTheTeam() {
        // Given: today is 2023-08-20 on the injected clock
        Clock fixedClock = Clock.fixed(LocalDate.of(2023, 8, 20).atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        given(this.clock.instant()).willReturn(fixedClock.instant());
        given(this.clock.getZone()).willReturn(fixedClock.getZone());
        Student john = this.students.get(0);
        Team team1 = new Team("Team1", "", "");
        john.setTeam(team1);
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));
        given(this.studentRepository.save(john)).willReturn(john);

        // When
        Student result = this.studentService.deactivateStudent(1);

        // Then
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 20));
        assertThat(result.getTeam()).isSameAs(team1);
    }

    @Test
    void testDeactivateStudentAlreadyDeactivated() {
        // Given: extension 3b
        Student john = this.students.get(0);
        john.deactivate(LocalDate.of(2023, 8, 16));
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));

        // When
        Throwable thrown = catchThrowable(() -> this.studentService.deactivateStudent(1));

        // Then
        assertThat(thrown).isInstanceOf(StudentAlreadyDeactivatedException.class).hasMessage("This student is already deactivated.");
        assertThat(john.getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 16));
        verify(this.studentRepository, never()).save(any(Student.class));
    }

    @Test
    void testDeleteStudent() {
        // Given
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        doNothing().when(this.studentRepository).deleteById(1);

        // When
        this.studentService.deleteStudent(1);

        // Then
        verify(this.studentRepository, times(1)).deleteById(1);
    }

}
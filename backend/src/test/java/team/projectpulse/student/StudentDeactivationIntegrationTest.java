package team.projectpulse.student;

import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.UserRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student, end to end through {@code POST /students/{studentId}/deactivation}.
 *
 * <p>Seed: Bingyang (admin) owns course 1 and is assigned to course section 2; Bill is an instructor assigned to
 * course section 2; Tim owns course 3 only. John (4), Eric (5) and Jerry (6) are Team1 in course section 2. Nora is
 * already deactivated. The dev clock reads 2023-08-20.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ActivityRepository activityRepository;

    @Autowired
    PeerEvaluationRepository peerEvaluationRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;
    String instructorBillToken;
    String adminTimToken;
    String studentJohnToken;
    String studentEricToken;

    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(Integer studentId, String token) throws Exception {
        return this.mockMvc.perform(post(this.baseUrl + "/students/" + studentId + "/deactivation").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private void unassignFromSection2(Integer instructorId) {
        Section section2 = this.sectionRepository.findById(2).orElseThrow();
        Instructor instructor = this.instructorRepository.findById(instructorId).orElseThrow();
        section2.getInstructors().remove(instructor);
        this.sectionRepository.saveAndFlush(section2);
    }

    private void assertStillActive(Integer studentId) {
        Student student = this.studentRepository.findById(studentId).orElseThrow();
        assertThat(student.isEnabled()).isTrue();
        assertThat(student.getDeactivatedOn()).isNull();
    }

    // Main success scenario

    @Test
    void testAssignedInstructorDeactivatesStudent() throws Exception {
        deactivate(5, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.deactivatedOn").value("2023-08-20")); // the dev clock's date (POST-3)
    }

    @Test
    void testCourseAdminNotAssignedToTheSectionViewsAndDeactivatesStudent() throws Exception {
        // Given: Bingyang owns course 1 but is no longer assigned to course section 2
        unassignFromSection2(1);

        // Step 2: she can view the student
        this.mockMvc.perform(get(this.baseUrl + "/students/5").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.adminBingyangToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Eric"));

        // Steps 3-6
        deactivate(5, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void testDeactivationKeepsTeamAndWork() throws Exception {
        // POST-4, BR-student-lifecycle
        long activitiesBefore = this.activityRepository.findAll().stream().filter(a -> a.getStudent().getId().equals(5)).count();
        long evaluationsBefore = this.peerEvaluationRepository.findAll().stream().filter(e -> e.getEvaluator().getId().equals(5)).count();
        assertThat(activitiesBefore).isPositive();
        assertThat(evaluationsBefore).isPositive();

        deactivate(5, this.instructorBillToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(1))
                .andExpect(jsonPath("$.data.teamName").value("Team1"));

        this.studentRepository.flush();
        assertThat(this.studentRepository.findById(5).orElseThrow().getTeam().getTeamId()).isEqualTo(1);
        assertThat(this.activityRepository.findAll().stream().filter(a -> a.getStudent().getId().equals(5)).count()).isEqualTo(activitiesBefore);
        assertThat(this.peerEvaluationRepository.findAll().stream().filter(e -> e.getEvaluator().getId().equals(5)).count()).isEqualTo(evaluationsBefore);
    }

    @Test
    void testDeactivatedStudentsOpenSessionStopsAtItsNextRequest() throws Exception {
        // POST-2, FR-SEC-active-account
        deactivate(5, this.instructorBillToken).andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/5").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentEricToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("e.hudson@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDeactivatedStudentNoLongerOwesAndIsNoLongerOwed() throws Exception {
        // BR-submission-owed. Given: John has evaluated every Team1 teammate for 2023-W32 except Jerry, so he owes
        this.peerEvaluationRepository.delete(this.peerEvaluationRepository.findByWeekAndEvaluatorIdAndEvaluateeId("2023-W32", 4, 6).orElseThrow());
        this.peerEvaluationRepository.flush();
        this.mockMvc.perform(get(this.baseUrl + "/sections/2/submission-status").param("item", "PEER_EVALUATION").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.data.teams[*].students[?(@.firstName == 'John')]", Matchers.hasSize(1)));

        // When: Jerry is deactivated
        deactivate(6, this.instructorBillToken).andExpect(status().isOk());

        // Then: John no longer owes an evaluation of Jerry, and Jerry owes nothing
        this.mockMvc.perform(get(this.baseUrl + "/sections/2/submission-status").param("item", "PEER_EVALUATION").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.data.teams[*].students[?(@.firstName == 'John')]", Matchers.empty()));
        this.mockMvc.perform(get(this.baseUrl + "/sections/2/submission-status").param("item", "WEEKLY_ACTIVITY_REPORT").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.data.teams[*].students[?(@.firstName == 'Jerry')]", Matchers.empty()));
    }

    // Extension 3a: not permitted (BR-student-lifecycle)

    @Test
    void testInstructorNotAssignedToTheSectionIsRefused() throws Exception {
        unassignFromSection2(2); // Bill, who does not own the course

        deactivate(5, this.instructorBillToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
        assertStillActive(5);
    }

    @Test
    void testCourseAdminOfAnotherCourseIsRefused() throws Exception {
        deactivate(5, this.adminTimToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
        assertStillActive(5);

        // Nor can she view the student (step 2)
        this.mockMvc.perform(get(this.baseUrl + "/students/5").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.adminTimToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    void testStudentCannotDeactivateHerself() throws Exception {
        deactivate(4, this.studentJohnToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
        assertStillActive(4);
    }

    @Test
    void testStudentCannotDeactivateAnotherStudent() throws Exception {
        deactivate(4, this.studentEricToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
        assertStillActive(4);
    }

    @Test
    void testUnknownStudentIsRefusedAtTheRoute() throws Exception {
        deactivate(9999, this.adminBingyangToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    // Extension 3b: already deactivated

    @Test
    void testAlreadyDeactivatedStudent() throws Exception {
        Integer noraId = this.userRepository.findByEmail("n.quinn@abc.edu").orElseThrow().getId();

        deactivate(noraId, this.instructorBillToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));
        assertThat(this.studentRepository.findById(noraId).orElseThrow().getDeactivatedOn()).isEqualTo(LocalDate.of(2023, 8, 16));
    }

    @Test
    void testSecondDeactivationIsAConflict() throws Exception {
        deactivate(5, this.instructorBillToken).andExpect(status().isOk());

        deactivate(5, this.instructorBillToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT));
    }

}

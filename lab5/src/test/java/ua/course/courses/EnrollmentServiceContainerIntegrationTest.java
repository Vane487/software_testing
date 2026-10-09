package ua.course.courses;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class EnrollmentServiceContainerIntegrationTest extends Lab5IntegrationTestSupport {

    @Test
    void containers_useRuntimeConfiguration_andMigrationIsApplied() throws Exception {
        assertAll(
                () -> assertTrue(POSTGRES.isRunning()),
                () -> assertTrue(WIREMOCK.isRunning()),
                () -> assertTrue(runtimeDatabaseDescription().contains("courses_variant04_test")),
                () -> assertTrue(runtimeWireMockDescription().startsWith("http://")),
                () -> assertEquals(0, enrollments.countOccupied(1))
        );
    }

    @Test
    void enroll_whenHttpPolicyAllows_persistsDiscountedState_andSendsExpectedRequest() throws Exception {
        Student student = createStudent("student-http-allowed");
        Course course = createCourse("course-http-allowed", 300_000, 2);
        stubPolicy(200, true);

        Enrollment result = service.enroll(
                new EnrollmentRequest("enrollment-http-allowed", student.id(), course.id(), false)
        );

        Enrollment persisted = enrollments.findById(result.id());
        assertAll(
                () -> assertEquals(EnrollmentStatus.Enrolled, persisted.status()),
                () -> assertEquals(264_000, persisted.total()),
                () -> assertEquals(264_000, persisted.prepaid()),
                () -> assertEquals(0, persisted.refund()),
                () -> assertEquals(1, enrollments.countOccupied(course.id()))
        );
        assertSinglePolicyRequest(student, course);
    }

    @Test
    void enroll_whenHttpPolicyRejects_doesNotPersist_andSendsExpectedRequest() throws Exception {
        Student student = createStudent("student-http-rejected");
        Course course = createCourse("course-http-rejected", 120_000, 2);
        stubPolicy(200, false);

        assertThrows(
                EnrollmentRejectedException.class,
                () -> service.enroll(
                        new EnrollmentRequest("enrollment-http-rejected", student.id(), course.id(), false)
                )
        );

        assertAll(
                () -> assertFalse(enrollments.hasEnrollment(student.id(), course.id())),
                () -> assertEquals(0, enrollments.countOccupied(course.id())),
                () -> assertTrue(
                        enrollments.findEnrollments(course.id(), EnrollmentStatus.Enrolled).isEmpty()
                )
        );
        assertSinglePolicyRequest(student, course);
    }

    @Test
    void enroll_whenHttpPolicyReturns503_doesNotPersist_andReportsDependencyFailure() throws Exception {
        Student student = createStudent("student-http-error");
        Course course = createCourse("course-http-error", 120_000, 2);
        stubPolicy(503, null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.enroll(
                        new EnrollmentRequest("enrollment-http-error", student.id(), course.id(), false)
                )
        );

        assertAll(
                () -> assertTrue(error.getMessage().contains("503")),
                () -> assertFalse(enrollments.hasEnrollment(student.id(), course.id())),
                () -> assertEquals(0, enrollments.countOccupied(course.id()))
        );
        assertSinglePolicyRequest(student, course);
    }

    @Test
    void enroll_whenCourseIsFull_rejectsLocally_withoutHttpRequestOrNewRecord() throws Exception {
        Student occupyingStudent = createStudent("student-occupying-place");
        Student rejectedStudent = createStudent("student-local-rejected");
        Course course = createCourse("course-full", 80_000, 1);
        enrollments.create(new Enrollment(
                0,
                "enrollment-occupying-place",
                occupyingStudent.id(),
                course.id(),
                80_000,
                80_000,
                0,
                EnrollmentStatus.Enrolled
        ));

        assertThrows(
                EnrollmentRejectedException.class,
                () -> service.enroll(
                        new EnrollmentRequest(
                                "enrollment-local-rejected",
                                rejectedStudent.id(),
                                course.id(),
                                false
                        )
                )
        );

        assertAll(
                () -> assertEquals(1, enrollments.countOccupied(course.id())),
                () -> assertFalse(enrollments.hasEnrollment(rejectedStudent.id(), course.id()))
        );
        assertNoPolicyRequests();
    }
}

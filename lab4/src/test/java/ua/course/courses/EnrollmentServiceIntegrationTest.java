package ua.course.courses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnrollmentServiceIntegrationTest extends IntegrationTestSupport {

    @Test
    void enroll_allowedStudent_createsEnrollment_serviceState() throws Exception {
        Student student = createStudent("student-positive");
        Course course = createCourse("course-positive", 80_000, 2);
        when(policy.isAllowed(student, course)).thenReturn(true);

        Enrollment result = service.enroll(
                new EnrollmentRequest("enrollment-positive", student.id(), course.id(), false)
        );

        Enrollment persisted = enrollments.findById(result.id());
        assertAll(
                () -> assertEquals(EnrollmentStatus.Enrolled, result.status()),
                () -> assertEquals(80_000, result.total()),
                () -> assertEquals(result, persisted),
                () -> assertEquals(1, enrollments.countOccupied(course.id()))
        );
        verify(policy).isAllowed(student, course);
    }

    @Test
    void enroll_whenCourseIsFull_rejectsWithoutInsert_serviceState() throws Exception {
        Student occupyingStudent = createStudent("student-occupying");
        Student rejectedStudent = createStudent("student-rejected");
        Course course = createCourse("course-full", 80_000, 1);
        enrollments.create(new Enrollment(
                0,
                "enrollment-occupying",
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
                        new EnrollmentRequest("enrollment-rejected", rejectedStudent.id(), course.id(), false)
                )
        );

        assertAll(
                () -> assertEquals(1, enrollments.countOccupied(course.id())),
                () -> assertEquals(1, enrollments.findEnrollments(course.id(), EnrollmentStatus.Enrolled).size()),
                () -> assertFalse(enrollments.hasEnrollment(rejectedStudent.id(), course.id()))
        );
        verifyNoInteractions(policy);
    }

    @Test
    void enroll_priceThresholds_applyFiveAndTwelvePercentDiscounts_persistedState() throws Exception {
        Student firstStudent = createStudent("student-five-percent");
        Student secondStudent = createStudent("student-twelve-percent");
        Course firstCourse = createCourse("course-1000-uah", 100_000, 1);
        Course secondCourse = createCourse("course-3000-uah", 300_000, 1);
        when(policy.isAllowed(firstStudent, firstCourse)).thenReturn(true);
        when(policy.isAllowed(secondStudent, secondCourse)).thenReturn(true);

        Enrollment fivePercent = service.enroll(
                new EnrollmentRequest("enrollment-five-percent", firstStudent.id(), firstCourse.id(), false)
        );
        Enrollment twelvePercent = service.enroll(
                new EnrollmentRequest("enrollment-twelve-percent", secondStudent.id(), secondCourse.id(), false)
        );

        Enrollment persistedFivePercent = enrollments.findById(fivePercent.id());
        Enrollment persistedTwelvePercent = enrollments.findById(twelvePercent.id());
        assertAll(
                () -> assertEquals(95_000, persistedFivePercent.total()),
                () -> assertEquals(95_000, persistedFivePercent.prepaid()),
                () -> assertEquals(264_000, persistedTwelvePercent.total()),
                () -> assertEquals(264_000, persistedTwelvePercent.prepaid())
        );
        verify(policy).isAllowed(firstStudent, firstCourse);
        verify(policy).isAllowed(secondStudent, secondCourse);
    }

    @Test
    void enroll_whenPolicyRejects_doesNotChangeDatabase() throws Exception {
        Student student = createStudent("student-policy-rejected");
        Course course = createCourse("course-policy-rejected", 120_000, 2);
        when(policy.isAllowed(student, course)).thenReturn(false);

        assertThrows(
                EnrollmentRejectedException.class,
                () -> service.enroll(
                        new EnrollmentRequest("enrollment-policy-rejected", student.id(), course.id(), false)
                )
        );

        assertAll(
                () -> assertFalse(enrollments.hasEnrollment(student.id(), course.id())),
                () -> assertEquals(0, enrollments.countOccupied(course.id())),
                () -> assertEquals(0, enrollments.findEnrollments(course.id(), EnrollmentStatus.Enrolled).size())
        );
        verify(policy).isAllowed(student, course);
    }

    @Test
    void cancel_enrolledStudent_refundsPrepaidAndFreesPlace() throws Exception {
        Student firstStudent = createStudent("student-cancelled");
        Student nextStudent = createStudent("student-after-cancel");
        Course course = createCourse("course-cancel", 150_000, 1);
        when(policy.isAllowed(firstStudent, course)).thenReturn(true);
        when(policy.isAllowed(nextStudent, course)).thenReturn(true);
        Enrollment original = service.enroll(
                new EnrollmentRequest("enrollment-to-cancel", firstStudent.id(), course.id(), false)
        );

        Enrollment cancelled = service.cancel(original.id(), 0);
        Enrollment persistedCancelled = enrollments.findById(original.id());
        Enrollment replacement = service.enroll(
                new EnrollmentRequest("enrollment-after-cancel", nextStudent.id(), course.id(), false)
        );

        assertAll(
                () -> assertEquals(EnrollmentStatus.Cancelled, cancelled.status()),
                () -> assertEquals(original.prepaid(), cancelled.refund()),
                () -> assertEquals(cancelled, persistedCancelled),
                () -> assertEquals(EnrollmentStatus.Enrolled, replacement.status()),
                () -> assertEquals(1, enrollments.countOccupied(course.id()))
        );
        verify(policy).isAllowed(firstStudent, course);
        verify(policy).isAllowed(nextStudent, course);
    }

}

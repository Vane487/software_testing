package ua.course.courses;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@TestMethodOrder(MethodOrderer.Random.class)
class EnrollmentRegressionIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6-alpine");

    static JdbcTemplate jdbc;

    StudentRepository students;
    CourseRepository courses;
    EnrollmentRepository enrollments;
    EnrollmentService service;

    @BeforeAll
    static void prepareDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .executeInTransaction(false)
                .load()
                .migrate();

        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
    }

    @BeforeEach
    void prepareIsolatedTestState() {
        resetDatabase();
        students = new StudentRepository(jdbc);
        courses = new CourseRepository(jdbc);
        enrollments = new EnrollmentRepository(jdbc);
        service = new EnrollmentService(students, courses, enrollments, (student, course) -> true);
    }

    @AfterEach
    void cleanTestState() {
        resetDatabase();
    }

    private Student student(boolean regular) throws Exception {
        return students.create(new Student(0, unique("ST"), 20, 90, regular));
    }

    private Course course() throws Exception {
        return courses.create(new Course(0, unique("CR"), 100_000, 10, 1));
    }

    private EnrollmentRequest request(Student student, Course course, boolean coupon) {
        return new EnrollmentRequest(unique("EN"), student.id(), course.id(), coupon);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12);
    }

    @Test
    @Tag("smoke")
    @DisplayName("Зарахування зберігається у БД")
    void createEnrollment() throws Exception {
        var saved = service.enroll(request(student(false), course(), false));

        assertEquals(saved, enrollments.findById(saved.id()));
        assertEquals(EnrollmentStatus.Enrolled, saved.status());
    }

    @Test
    @DisplayName("Скасування повертає передоплату")
    void cancelEnrollment() throws Exception {
        var enrolled = service.enroll(request(student(false), course(), false));

        var cancelled = service.cancel(enrolled.id(), 7);

        assertEquals(EnrollmentStatus.Cancelled, cancelled.status());
        assertEquals(cancelled.prepaid(), cancelled.refund());
        assertEquals(cancelled, enrollments.findById(enrolled.id()));
    }

    @Test
    @DisplayName("Картка слухача зберігає вік")
    void studentAge() throws Exception {
        var saved = students.create(new Student(0, unique("ST-AGE"), 20, 90, false));

        assertEquals(20, students.findById(saved.id()).age());
    }

    @Test
    @DisplayName("Картка слухача зберігає вступний бал")
    void studentScore() throws Exception {
        var saved = students.create(new Student(0, unique("ST-SCORE"), 25, 85, true));

        assertEquals(85, students.findById(saved.id()).entryScore());
    }

    @Test
    @Timeout(5)
    @DisplayName("Зарахування після зовнішнього дозволу")
    void enrollmentAfterPolicyCheck() throws Exception {
        var selectedCourse = course();
        var enrollmentRequest = request(student(false), selectedCourse, false);
        var policyStarted = new CountDownLatch(1);
        var allowPolicy = new CountDownLatch(1);

        var delayedService = new EnrollmentService(students, courses, enrollments, (student, value) -> {
            policyStarted.countDown();
            try {
                if (!allowPolicy.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Політика не отримала рішення вчасно");
                }
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Перевірку перервано", exception);
            }
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var operation = CompletableFuture.supplyAsync(() -> {
                try {
                    return delayedService.enroll(enrollmentRequest);
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            }, executor);

            assertTrue(policyStarted.await(2, TimeUnit.SECONDS));
            assertTrue(enrollments
                    .findEnrollments(selectedCourse.id(), EnrollmentStatus.Enrolled)
                    .isEmpty());

            allowPolicy.countDown();
            var saved = operation.get(2, TimeUnit.SECONDS);

            assertEquals(enrollmentRequest.reference(), saved.reference());
            assertEquals(saved, enrollments.findById(saved.id()));
            assertEquals(95_000, saved.total());
        }
    }

    @Test
    @DisplayName("Вибірка містить дві заявки курсу незалежно від порядку")
    void enrollmentSelection() throws Exception {
        var selectedCourse = course();
        var expectedReferences = List.of(unique("EN-A"), unique("EN-B"));
        var fixtureOrder = Boolean.getBoolean("regression.reverseFixture")
                ? expectedReferences.reversed()
                : expectedReferences;

        for (String reference : fixtureOrder) {
            service.enroll(new EnrollmentRequest(
                    reference, student(false).id(), selectedCourse.id(), false));
        }

        var actualReferences = enrollments
                .findEnrollments(selectedCourse.id(), EnrollmentStatus.Enrolled)
                .stream()
                .map(Enrollment::reference)
                .toList();

        assertEquals(new HashSet<>(expectedReferences), new HashSet<>(actualReferences));
    }

    @Test
    @Tag("smoke")
    @DisplayName("Завершення зберігає стан заявки")
    void completion() throws Exception {
        var saved = service.enroll(request(student(false), course(), false));

        var completed = service.complete(saved.id());

        assertEquals(EnrollmentStatus.Completed, completed.status());
        assertEquals(completed, enrollments.findById(saved.id()));
    }

    @Test
    @DisplayName("Повторне зарахування відхиляється")
    void duplicateEnrollment() throws Exception {
        var selectedStudent = student(false);
        var selectedCourse = course();
        var saved = service.enroll(request(selectedStudent, selectedCourse, false));

        assertThrows(EnrollmentRejectedException.class,
                () -> service.enroll(request(selectedStudent, selectedCourse, false)));
        assertEquals(saved, enrollments.findById(saved.id()));
        assertEquals(1, enrollments.countOccupied(selectedCourse.id()));
    }

    @Test
    @Tag("smoke")
    @DisplayName("Зовнішня відмова не створює заявку")
    void policyRejection() throws Exception {
        var selectedCourse = course();
        var enrollmentRequest = request(student(false), selectedCourse, false);
        var denied = new EnrollmentService(
                students, courses, enrollments, (student, course) -> false);

        assertThrows(EnrollmentRejectedException.class,
                () -> denied.enroll(enrollmentRequest));
        assertTrue(enrollments
                .findEnrollments(selectedCourse.id(), EnrollmentStatus.Enrolled)
                .isEmpty());
    }

    @Test
    @DisplayName("Повторне скасування не змінює заявку")
    void repeatedCancellation() throws Exception {
        var saved = service.enroll(request(student(false), course(), false));
        var cancelled = service.cancel(saved.id(), 7);

        assertThrows(EnrollmentRejectedException.class,
                () -> service.cancel(saved.id(), 7));
        assertEquals(cancelled, enrollments.findById(saved.id()));
    }

    private void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE enrollments, courses, students RESTART IDENTITY CASCADE");
    }
}

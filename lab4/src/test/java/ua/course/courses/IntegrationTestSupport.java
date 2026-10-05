package ua.course.courses;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

abstract class IntegrationTestSupport {
    protected Connection connection;
    protected StudentRepository students;
    protected CourseRepository courses;
    protected EnrollmentRepository enrollments;
    protected EnrollmentPolicy policy;
    protected EnrollmentService service;

    @BeforeEach
    void openDatabaseAndResetState() throws Exception {
        connection = Database.connect();
        assertTrue(connection.getAutoCommit(), "Integration tests require autoCommit=true");
        resetDatabase();

        students = new StudentRepository(connection);
        courses = new CourseRepository(connection);
        enrollments = new EnrollmentRepository(connection);
        policy = mock(EnrollmentPolicy.class);
        service = new EnrollmentService(students, courses, enrollments, policy);
    }

    @AfterEach
    void resetStateAndCloseDatabase() throws Exception {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                resetDatabase();
            }
        } finally {
            connection.close();
        }
    }

    protected Student createStudent(String code) throws SQLException {
        return students.create(new Student(0, code, 20, 80, true));
    }

    protected Course createCourse(String code, long price, int capacity) throws SQLException {
        return courses.create(new Course(0, code, price, capacity, 1));
    }

    private void resetDatabase() throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE enrollments, courses, students RESTART IDENTITY CASCADE");
        }
    }
}

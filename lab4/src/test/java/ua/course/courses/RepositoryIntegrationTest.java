package ua.course.courses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryIntegrationTest extends IntegrationTestSupport {

    @Test
    void studentCreateAndFind_returnsPersistedStudent_repositoryState() throws Exception {
        Student created = students.create(new Student(999, "student-repository", 19, 88, true));

        Student persisted = students.findById(created.id());

        assertAll(
                () -> assertTrue(created.id() > 0),
                () -> assertEquals(created, persisted),
                () -> assertEquals("student-repository", persisted.code()),
                () -> assertEquals(88, persisted.entryScore())
        );
    }

    @Test
    void courseCreateAndFind_returnsPersistedCourse_repositoryState() throws Exception {
        Course created = courses.create(new Course(999, "course-repository", 125_000, 25, 2));

        Course persisted = courses.findById(created.id());

        assertAll(
                () -> assertTrue(created.id() > 0),
                () -> assertEquals(created, persisted),
                () -> assertEquals(125_000, persisted.price()),
                () -> assertEquals(25, persisted.capacity())
        );
    }

    @Test
    void studentUpdateAndDelete_changesDatabaseState() throws Exception {
        Student created = createStudent("student-before-update");
        Student updated = new Student(created.id(), "student-after-update", 21, 95, false);

        students.update(updated);

        assertEquals(updated, students.findById(created.id()));

        students.delete(created.id());

        assertNull(students.findById(created.id()));
    }
}

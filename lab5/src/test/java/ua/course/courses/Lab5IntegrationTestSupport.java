package ua.course.courses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class Lab5IntegrationTestSupport {
    private static final int WIREMOCK_PORT = 8080;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient ADMIN_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName("courses_variant04_test")
            .withUsername("courses_student")
            .withPassword("courses_password")
            .withInitScript("migrations/V1__create_tables.sql");

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(
            DockerImageName.parse("wiremock/wiremock:3.13.2-2")
    )
            .withExposedPorts(WIREMOCK_PORT)
            .waitingFor(
                    Wait.forHttp("/__admin/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(60))
            );

    protected Connection connection;
    protected StudentRepository students;
    protected CourseRepository courses;
    protected EnrollmentRepository enrollments;
    protected EnrollmentService service;

    @BeforeEach
    void prepareIsolatedEnvironment() throws Exception {
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        assertTrue(connection.getAutoCommit(), "Integration tests require autoCommit=true");

        resetDatabase();
        resetWireMock();

        students = new StudentRepository(connection);
        courses = new CourseRepository(connection);
        enrollments = new EnrollmentRepository(connection);
        var policy = new HttpEnrollmentPolicy(
                URI.create(wireMockBaseUrl()),
                Duration.ofSeconds(3)
        );
        service = new EnrollmentService(students, courses, enrollments, policy);
    }

    @AfterEach
    void cleanEnvironment() throws Exception {
        try {
            if (connection != null && !connection.isClosed()) {
                resetDatabase();
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
            resetWireMock();
        }
    }

    protected Student createStudent(String code) throws SQLException {
        return students.create(new Student(0, code, 20, 80, true));
    }

    protected Course createCourse(String code, long price, int capacity) throws SQLException {
        return courses.create(new Course(0, code, price, capacity, 1));
    }

    protected void stubPolicy(int status, Boolean allowed) throws Exception {
        ObjectNode mapping = JSON.createObjectNode();
        mapping.putObject("request")
                .put("method", "POST")
                .put("urlPath", "/enrollment-policy");

        ObjectNode response = mapping.putObject("response");
        response.put("status", status);
        if (allowed != null) {
            response.putObject("headers").put("Content-Type", "application/json");
            response.putObject("jsonBody").put("allowed", allowed);
        }

        sendAdmin("POST", "/__admin/mappings", JSON.writeValueAsString(mapping));
    }

    protected void assertSinglePolicyRequest(Student student, Course course) throws Exception {
        List<JsonNode> requests = policyRequests();
        assertEquals(1, requests.size(), "Exactly one policy request is expected");

        JsonNode request = requests.getFirst();
        JsonNode body = JSON.readTree(request.path("body").asText());
        assertEquals("POST", request.path("method").asText());
        assertEquals("/enrollment-policy", request.path("url").asText());
        assertEquals(student.id(), body.path("studentId").asLong());
        assertEquals(course.id(), body.path("courseId").asLong());
    }

    protected void assertNoPolicyRequests() throws Exception {
        assertEquals(0, policyRequests().size(), "A local rejection must not call the policy");
    }

    protected String runtimeDatabaseDescription() {
        return POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + POSTGRES.getDatabaseName();
    }

    protected String runtimeWireMockDescription() {
        return wireMockBaseUrl();
    }

    private void resetDatabase() throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE enrollments, courses, students RESTART IDENTITY CASCADE");
        }
    }

    private void resetWireMock() throws Exception {
        sendAdmin("POST", "/__admin/reset", "");
    }

    private List<JsonNode> policyRequests() throws Exception {
        JsonNode response = JSON.readTree(sendAdmin("GET", "/__admin/requests", null));
        List<JsonNode> requests = new ArrayList<>();
        for (JsonNode entry : response.path("requests")) {
            JsonNode request = entry.path("request");
            if ("/enrollment-policy".equals(request.path("url").asText())) {
                requests.add(request);
            }
        }
        return requests;
    }

    private String wireMockBaseUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(WIREMOCK_PORT);
    }

    private String sendAdmin(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(wireMockBaseUrl() + path))
                .timeout(Duration.ofSeconds(5));

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        HttpResponse<String> response = ADMIN_CLIENT.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "WireMock admin request failed: " + response.statusCode() + " " + response.body()
            );
        }
        return response.body();
    }
}

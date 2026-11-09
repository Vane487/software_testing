package ua.course.courses;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class EnrollmentServiceUnitTest {

    EnrollmentService service;

    @BeforeEach
    void createUnit() {
        service = new EnrollmentService(
                mock(StudentRepository.class),
                mock(CourseRepository.class),
                mock(EnrollmentRepository.class),
                mock(EnrollmentPolicy.class));
    }

    @ParameterizedTest(name = "price={0} -> total={1}")
    @CsvSource({
            "99999, 99999",
            "100000, 95000",
            "299999, 285000",
            "300000, 264000"
    })
    void calculateTotal_appliesVariant04PriceBoundaries(long price, long expectedTotal) {
        assertEquals(expectedTotal, service.calculateTotal(price, false, false));
    }

    @Test
    void calculateTotal_rejectsPriceOutsideDomain() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.calculateTotal(99, false, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.calculateTotal(10_000_001, false, false))
        );
    }

    @Test
    void calculateRefund_returnsFullPrepayment() {
        assertEquals(95_000, service.calculateRefund(95_000, 7));
    }

    @Test
    void isEligible_acceptsValidVariantParameters() {
        assertEquals(true, service.isEligible(20, 90, 1, false));
    }
}

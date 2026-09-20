import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CarRentalServiceTest {
    private CarRentalService.FleetAvailability fleet;

    private CarRentalService.DriverHistory history;

    private CarRentalService.RentalRepository rentals;

    private CarRentalService service;

    @BeforeEach
    void setUp() {
        fleet = mock(CarRentalService.FleetAvailability.class);
        history = mock(CarRentalService.DriverHistory.class);
        rentals = mock(CarRentalService.RentalRepository.class);
        service = new CarRentalService(fleet, history, rentals);
    }

    @Test
    void process_regularCarWithoutInsurance_returnsExpectedAmountsAndSavesExactArguments() {
        CarRentalService.Request request = new CarRentalService.Request(40, 3, false, false);
        when(fleet.isAvailable()).thenReturn(true);

        CarRentalService.Result result = service.process(request);

        assertAll(
                () -> assertEquals("Оформлено", result.status()),
                () -> assertEquals(150_000L, result.rental()),
                () -> assertEquals(0L, result.ageSurcharge()),
                () -> assertEquals(0L, result.insurance()),
                () -> assertEquals(200_000L, result.deposit()),
                () -> assertEquals(350_000L, result.total())
        );

        ArgumentCaptor<CarRentalService.Request> requestCaptor =
                ArgumentCaptor.forClass(CarRentalService.Request.class);
        ArgumentCaptor<CarRentalService.Result> resultCaptor =
                ArgumentCaptor.forClass(CarRentalService.Result.class);

        verify(rentals).save(requestCaptor.capture(), resultCaptor.capture());
        assertEquals(request, requestCaptor.getValue());
        assertEquals(result, resultCaptor.getValue());
        verifyNoInteractions(history);
    }

    @Test
    void process_insuredTenDayRental_appliesDiscountInsuranceAndReducedDeposit() {
        CarRentalService.Request request = new CarRentalService.Request(40, 10, false, true);
        when(fleet.isAvailable()).thenReturn(true);

        CarRentalService.Result result = service.process(request);

        assertAll(
                () -> assertEquals("Оформлено", result.status()),
                () -> assertEquals(450_000L, result.rental()),
                () -> assertEquals(0L, result.ageSurcharge()),
                () -> assertEquals(80_000L, result.insurance()),
                () -> assertEquals(100_000L, result.deposit()),
                () -> assertEquals(630_000L, result.total())
        );
        verify(rentals).save(request, result);
        verifyNoInteractions(history);
    }

    @Test
    void process_unavailableCar_rejectsWithoutHistoryOrRepositoryCalls() {
        CarRentalService.Request request = new CarRentalService.Request(30, 5, false, false);
        when(fleet.isAvailable()).thenReturn(false);

        CarRentalService.Result result = service.process(request);

        assertEquals(new CarRentalService.Result("Немає автомобіля", 0, 0, 0, 0, 0), result);
        verify(fleet).isAvailable();
        verifyNoInteractions(history, rentals);
    }

    @Test
    void process_youngPremiumDriverWithIncident_rejectsWithoutSaving() {
        CarRentalService.Request request = new CarRentalService.Request(23, 3, true, false);
        when(fleet.isAvailable()).thenReturn(true);
        when(history.hasIncident()).thenReturn(true);

        CarRentalService.Result result = service.process(request);

        assertEquals(new CarRentalService.Result("Відмова за історією", 0, 0, 0, 0, 0), result);
        verify(history).hasIncident();
        verifyNoInteractions(rentals);
    }

    @ParameterizedTest(name = "age={0}, days={1}")
    @CsvSource({
            "20, 5",
            "76, 5",
            "30, 0",
            "30, 31"
    })
    void process_invalidRanges_throwsBeforeCallingDependencies(int age, int days) {
        CarRentalService.Request request = new CarRentalService.Request(age, days, false, false);

        assertThrows(IllegalArgumentException.class, () -> service.process(request));

        verifyNoInteractions(fleet, history, rentals);
    }

    @Test
    void process_fleetFailure_propagatesExceptionAndSkipsOtherDependencies() {
        CarRentalService.Request request = new CarRentalService.Request(30, 5, false, false);
        IllegalStateException failure = new IllegalStateException("Fleet service unavailable");
        when(fleet.isAvailable()).thenThrow(failure);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> service.process(request));

        assertSame(failure, thrown);
        verify(fleet).isAvailable();
        verifyNoInteractions(history, rentals);
    }

    @Test
    void process_historyFailure_propagatesExceptionAndDoesNotSave() {
        CarRentalService.Request request = new CarRentalService.Request(23, 3, true, false);
        IllegalStateException failure = new IllegalStateException("History service unavailable");
        when(fleet.isAvailable()).thenReturn(true);
        when(history.hasIncident()).thenThrow(failure);

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> service.process(request));

        assertSame(failure, thrown);
        verify(history).hasIncident();
        verifyNoInteractions(rentals);
    }

    @Test
    void process_age75IsRejected_counterRequirement() {
        // Вимога: вік 21-75 включно. Реалізація помилково встановлює максимум 74.
        CarRentalService.Request request = new CarRentalService.Request(75, 5, false, false);

        assertThrows(IllegalArgumentException.class, () -> service.process(request));

        verifyNoInteractions(fleet, history, rentals);
    }

    @Test
    void process_sevenDaysHaveNoDiscount_counterRequirement() {
        // Вимога: знижка діє з 7 діб. Реалізація застосовує її лише коли days > 7.
        CarRentalService.Request request = new CarRentalService.Request(40, 7, false, false);
        when(fleet.isAvailable()).thenReturn(true);

        CarRentalService.Result result = service.process(request);

        assertAll(
                () -> assertEquals(350_000L, result.rental()),
                () -> assertEquals(550_000L, result.total())
        );
        verify(rentals).save(request, result);
    }

    @ParameterizedTest(name = "age={0}, premium={1}")
    @CsvSource({
            "23, false",
            "40, true"
    })
    void process_historyIsSkippedWhenExactlyOneTriggerApplies_counterRequirement(
            int age,
            boolean premium
    ) {
        // Вимога: історія перевіряється для young OR premium. У коді використано AND.
        CarRentalService.Request request = new CarRentalService.Request(age, 3, premium, false);
        when(fleet.isAvailable()).thenReturn(true);

        CarRentalService.Result result = service.process(request);

        assertEquals("Оформлено", result.status());
        verifyNoInteractions(history);
        verify(rentals).save(request, result);
    }
}

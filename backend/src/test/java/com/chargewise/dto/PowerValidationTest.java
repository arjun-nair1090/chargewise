package com.chargewise.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** kW/kWh inputs carry at most two decimals so the watt conversion is exact (architecture D1). */
class PowerValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static VehicleRequest vehicle(String maxChargingKw, String soc, Set<ConnectorType> types) {
        return new VehicleRequest("MH01AB1234", new BigDecimal("40.50"), new BigDecimal(soc),
                new BigDecimal(maxChargingKw), types);
    }

    private static VehicleRequest vehicleWithMaxKw(String maxChargingKw) {
        return vehicle(maxChargingKw, "20.00", Set.of(ConnectorType.CCS2));
    }

    private static ConnectorRequest connectorWithMaxKw(String maxPowerKw) {
        return new ConnectorRequest(ConnectorType.CCS2, new BigDecimal(maxPowerKw), ConnectorStatus.OPERATIONAL);
    }

    private static Set<String> violatedProperties(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void powerWithThreeDecimalsIsRejected() {
        assertThat(validator.validate(vehicleWithMaxKw("7.125")))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("maxChargingKw"));
        assertThat(validator.validate(connectorWithMaxKw("7.125")))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("maxPowerKw"));

        assertThat(validator.validate(vehicleWithMaxKw("7.13"))).isEmpty();
        assertThat(validator.validate(connectorWithMaxKw("7.13"))).isEmpty();
    }

    @Test
    void nonPositivePowerRejected() {
        assertThat(violatedProperties(vehicleWithMaxKw("0"))).containsExactly("maxChargingKw");
        assertThat(violatedProperties(connectorWithMaxKw("-7.40"))).containsExactly("maxPowerKw");
    }

    @Test
    void socOutsideZeroToHundredRejected() {
        assertThat(violatedProperties(vehicle("50.00", "100.01", Set.of(ConnectorType.CCS2))))
                .containsExactly("currentSoc");
        assertThat(violatedProperties(vehicle("50.00", "-0.01", Set.of(ConnectorType.CCS2))))
                .containsExactly("currentSoc");
        assertThat(validator.validate(vehicle("50.00", "100", Set.of(ConnectorType.CCS2)))).isEmpty();
        assertThat(validator.validate(vehicle("50.00", "0", Set.of(ConnectorType.CCS2)))).isEmpty();
    }

    @Test
    void emptyConnectorTypesRejected() {
        assertThat(violatedProperties(vehicle("50.00", "20.00", Set.of()))).containsExactly("connectorTypes");
    }

    @Test
    void missingFieldsRejected() {
        assertThat(violatedProperties(new VehicleRequest(" ", null, null, null, null)))
                .containsExactlyInAnyOrder("registrationNumber", "batteryCapacityKwh", "currentSoc",
                        "maxChargingKw", "connectorTypes");
        assertThat(violatedProperties(new ConnectorRequest(null, null, null)))
                .containsExactlyInAnyOrder("connectorType", "maxPowerKw", "status");
    }
}

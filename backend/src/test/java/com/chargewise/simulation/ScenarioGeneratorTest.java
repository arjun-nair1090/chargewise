package com.chargewise.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ScenarioGeneratorTest {

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void sameConfigAndSeedProduceIdenticalInputHash(Scenario scenario) {
        SimulationConfig config = SimulationConfig.preset(scenario);

        ScenarioInput first = ScenarioGenerator.generate(config, 20261102L);
        ScenarioInput second = ScenarioGenerator.generate(config, 20261102L);

        assertThat(second.canonicalText()).isEqualTo(first.canonicalText());
        assertThat(second.inputHash()).isEqualTo(first.inputHash()).hasSize(64);
    }

    @Test
    void differentSeedProducesDifferentInput() {
        SimulationConfig config = SimulationConfig.preset(Scenario.NORMAL);

        assertThat(ScenarioGenerator.generate(config, 1L).inputHash())
                .isNotEqualTo(ScenarioGenerator.generate(config, 2L).inputHash());
    }

    @Test
    void differentScenarioWithSameSeedProducesDifferentInput() {
        assertThat(ScenarioGenerator.generate(SimulationConfig.preset(Scenario.NORMAL), 7L).inputHash())
                .isNotEqualTo(ScenarioGenerator.generate(SimulationConfig.preset(Scenario.GRID_REDUCTION), 7L).inputHash());
    }

    @ParameterizedTest
    @EnumSource(Scenario.class)
    void generatedInputRespectsTheConfiguredShape(Scenario scenario) {
        SimulationConfig config = SimulationConfig.preset(scenario);
        ScenarioInput input = ScenarioGenerator.generate(config, 42L);

        assertThat(input.stations()).hasSize(config.stations());
        assertThat(input.connectors()).hasSize(config.stations() * config.connectorsPerStation());
        assertThat(input.requests()).hasSize(config.requests());
        for (ScenarioInput.SimRequest request : input.requests()) {
            assertThat(request.arrivalSlot()).isBetween(0, config.horizonSlots() - 1);
            assertThat(request.latestStartSlot()).isEqualTo(request.arrivalSlot() + config.maxWaitSlots());
            assertThat(request.station()).isBetween(0, config.stations() - 1);
            assertThat(request.connectorTypeMask()).isPositive();
            assertThat(request.energyWh()).isPositive();
            assertThat(request.vehicleMaxW()).isPositive();
        }
        for (ScenarioInput.SimConnector connector : input.connectors()) {
            assertThat(connector.maxW()).isPositive();
            assertThat(connector.station()).isBetween(0, config.stations() - 1);
        }
        assertThat(input.feeders() != null).isEqualTo(scenario == Scenario.SHARED_FEEDER);
    }

    @Test
    void eventScenariosCarryTheirEvents() {
        ScenarioInput connectorOutage = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.CONNECTOR_OUTAGE), 3L);
        ScenarioInput stationOutage = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.STATION_OUTAGE), 3L);
        ScenarioInput gridReduction = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.GRID_REDUCTION), 3L);
        ScenarioInput normal = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.NORMAL), 3L);
        int middle = SimulationConfig.preset(Scenario.NORMAL).horizonSlots() / 2;

        assertThat(connectorOutage.connectors()).anyMatch(c -> !connectorOutage.connectorOperational(c.index(), middle));
        assertThat(stationOutage.stations()).anyMatch(s -> !stationOutage.stationOperational(s.index(), middle));
        assertThat(gridReduction.gridFactorPercent(middle)).isLessThan(100);
        assertThat(gridReduction.gridFactorPercent(0)).isEqualTo(100);
        assertThat(normal.connectors()).allMatch(c -> normal.connectorOperational(c.index(), middle));
        assertThat(normal.gridFactorPercent(middle)).isEqualTo(100);
    }

    @Test
    void sharedFeederScenarioHasDualFedStations() {
        ScenarioInput input = ScenarioGenerator.generate(SimulationConfig.preset(Scenario.SHARED_FEEDER), 11L);

        assertThat(input.feeders().feederCount()).isGreaterThan(1);
        assertThat(input.feeders().linkCount()).isGreaterThan(input.stations().size());
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new SimulationConfig(Scenario.NORMAL, 0, 4, 100, 96, 8))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stations");
        assertThatThrownBy(() -> new SimulationConfig(Scenario.NORMAL, 10, 4, 100, 0, 8))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("horizonSlots");
        assertThatThrownBy(() -> new SimulationConfig(null, 10, 4, 100, 96, 8))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scenario");
    }
}

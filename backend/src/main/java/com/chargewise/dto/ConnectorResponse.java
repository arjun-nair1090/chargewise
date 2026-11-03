package com.chargewise.dto;

import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.ConnectorType;
import java.math.BigDecimal;

public record ConnectorResponse(Long id, Long stationId, ConnectorType connectorType, BigDecimal maxPowerKw,
        ConnectorStatus status) {

    public static ConnectorResponse from(Connector connector) {
        return new ConnectorResponse(connector.getId(), connector.getStation().getId(), connector.getConnectorType(),
                connector.getMaxPowerKw(), connector.getStatus());
    }
}

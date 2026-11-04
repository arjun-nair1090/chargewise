package com.chargewise.dto;

import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import java.math.BigDecimal;
import java.util.List;

public record StationResponse(Long id, String name, String address, String city, String state, BigDecimal latitude,
        BigDecimal longitude, BigDecimal gridCapacityKw, StationStatus status, List<ConnectorResponse> connectors) {

    public static StationResponse from(Station station, List<ConnectorResponse> connectors) {
        return new StationResponse(station.getId(), station.getName(), station.getAddress(), station.getCity(),
                station.getState(), station.getLatitude(), station.getLongitude(), station.getGridCapacityKw(),
                station.getStatus(), List.copyOf(connectors));
    }
}

package com.chargewise.service;

import com.chargewise.entity.Connector;
import com.chargewise.entity.ConnectorStatus;
import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import java.math.BigDecimal;

/**
 * Reacts to operator changes inside the same transaction: outages and grid reductions must update affected
 * bookings and sessions atomically with the change itself (architecture D5).
 */
public interface StationChangeListener {

    void stationChanged(Station station, StationStatus previousStatus, BigDecimal previousGridKw);

    void connectorChanged(Connector connector, ConnectorStatus previousStatus);
}

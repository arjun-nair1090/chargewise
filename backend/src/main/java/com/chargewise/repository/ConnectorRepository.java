package com.chargewise.repository;

import com.chargewise.entity.Connector;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConnectorRepository extends JpaRepository<Connector, Long> {

    List<Connector> findAllByStationIdOrderByIdAsc(Long stationId);

    List<Connector> findAllByStationIdInOrderByIdAsc(Collection<Long> stationIds);

    @Query("select c.status, count(c) from Connector c group by c.status")
    List<Object[]> countByStatus();

    /** Connectors that can charge right now: operational, at an operational station. */
    @Query("""
            select count(c) from Connector c
            where c.status = com.chargewise.entity.ConnectorStatus.OPERATIONAL
              and c.station.status = com.chargewise.entity.StationStatus.OPERATIONAL""")
    long countUsable();
}

package com.chargewise.repository;

import com.chargewise.entity.Station;
import com.chargewise.entity.StationStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StationRepository extends JpaRepository<Station, Long> {

    Page<Station> findAllByStatus(StationStatus status, Pageable pageable);

    @Query("select s.status, count(s) from Station s group by s.status")
    List<Object[]> countByStatus();

    @Query("""
            select coalesce(sum(s.gridCapacityKw), 0) from Station s
            where s.status = com.chargewise.entity.StationStatus.OPERATIONAL""")
    BigDecimal operationalGridKw();

    interface NearbyRow {
        Long getId();

        Double getDistanceKm();
    }

    /** Haversine great-circle distance in SQL (mean Earth radius 6371.0088 km), nearest first. */
    @Query(value = """
            select id, distance_km as "distanceKm" from (
                select s.id,
                       6371.0088 * 2 * asin(sqrt(
                           power(sin(radians(s.latitude - :lat) / 2), 2)
                           + cos(radians(:lat)) * cos(radians(s.latitude))
                             * power(sin(radians(s.longitude - :lng) / 2), 2))) as distance_km
                from stations s
                where s.latitude is not null and s.longitude is not null) d
            where distance_km <= :radiusKm
            order by distance_km, id
            limit :maxResults""", nativeQuery = true)
    List<NearbyRow> findNearby(@Param("lat") double lat, @Param("lng") double lng,
            @Param("radiusKm") double radiusKm, @Param("maxResults") int maxResults);
}

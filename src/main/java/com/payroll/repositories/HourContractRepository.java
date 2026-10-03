package com.payroll.repositories;

import com.payroll.domain.HourContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HourContractRepository extends JpaRepository<HourContract, Long> {

    /**
     * Soma (hours * valuePerHour) dos contratos do funcionário no intervalo [start, end).
     * O intervalo semiaberto cobre o mês inteiro e permite uso de índice em date.
     */
    @Query("""
            SELECT COALESCE(SUM(c.hours * c.valuePerHour), 0)
            FROM HourContract c
            WHERE c.worker.id = :workerId
              AND c.date >= :start AND c.date < :end
            """)
    BigDecimal sumContractsValue(@Param("workerId") Long workerId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);
}

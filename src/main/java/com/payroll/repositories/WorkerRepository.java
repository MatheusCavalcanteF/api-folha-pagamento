package com.payroll.repositories;

import com.payroll.domain.Worker;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    // Evita N+1 ao montar o departmentName de cada funcionário da página
    @Override
    @EntityGraph(attributePaths = "department")
    Page<Worker> findAll(Pageable pageable);

    // Carrega department e position junto, para o cálculo de renda
    @Override
    @EntityGraph(attributePaths = {"department", "position"})
    Optional<Worker> findById(Long id);
}

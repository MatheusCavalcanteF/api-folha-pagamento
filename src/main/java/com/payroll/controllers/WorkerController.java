package com.payroll.controllers;

import com.payroll.dto.IncomeResponseDTO;
import com.payroll.dto.WorkerDTO;
import com.payroll.services.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping
    public ResponseEntity<Page<WorkerDTO>> findAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(workerService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkerDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(workerService.findById(id));
    }

    @GetMapping("/{id}/income")
    public ResponseEntity<IncomeResponseDTO> calculateIncome(
            @PathVariable Long id,
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(workerService.calculateIncome(id, year, month));
    }
}

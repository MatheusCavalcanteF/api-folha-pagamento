package com.payroll.dto;

import java.math.BigDecimal;

public record IncomeResponseDTO(
        String workerName,
        String departmentName,
        BigDecimal baseSalary,
        int month,
        int year,
        BigDecimal totalIncome) {
}

package com.payroll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TODO (débito técnico - histórico salarial): para evitar a alteração retroativa da renda ao mudar
 * o salário do cargo atual, a próxima iteração do sistema deverá implementar uma entidade
 * {@code Payroll} (Fechamento de Folha) para registrar o snapshot (foto) do salário-base exato no
 * momento do cálculo, ou implementar vigência temporal na entidade {@code Position}
 * (ex.: valores com data de início/fim de vigência).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(precision = 12, scale = 2)
    private BigDecimal baseSalary;
}

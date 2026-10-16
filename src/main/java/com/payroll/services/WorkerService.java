package com.payroll.services;

import com.payroll.domain.Department;
import com.payroll.domain.Worker;
import com.payroll.domain.WorkerStatus;
import com.payroll.dto.IncomeResponseDTO;
import com.payroll.dto.WorkerDTO;
import com.payroll.infra.BusinessRuleException;
import com.payroll.infra.InvalidParameterException;
import com.payroll.infra.ResourceNotFoundException;
import com.payroll.repositories.HourContractRepository;
import com.payroll.repositories.WorkerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkerService {

    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 2100;

    private final WorkerRepository workerRepository;
    private final HourContractRepository hourContractRepository;

    public Page<WorkerDTO> findAll(Pageable pageable) {
        return workerRepository.findAll(pageable).map(this::toDTO);
    }

    public WorkerDTO findById(Long id) {
        return toDTO(getWorker(id));
    }

    public IncomeResponseDTO calculateIncome(Long workerId, int year, int month) {
        // Sem esse limite, um ano fora do suportado por LocalDate vira 500 e um ano negativo vira 200.
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new InvalidParameterException("Ano inválido: " + year + " (use " + MIN_YEAR + " a " + MAX_YEAR + ")");
        }
        if (month < 1 || month > 12) {
            throw new InvalidParameterException("Mês inválido: " + month + " (use 1 a 12)");
        }

        Worker worker = getWorker(workerId);

        // Regra 1: funcionário inativo não entra na folha
        if (worker.getStatus() == WorkerStatus.INATIVO) {
            throw new BusinessRuleException("Não é possível processar folha de pagamento para funcionário inativo");
        }

        // TODO (débito técnico - histórico salarial): o baseSalary abaixo é lido do cargo ATUAL
        //  (Position), então alterar o salário do cargo muda retroativamente a renda de meses já
        //  calculados. Para evitar a alteração retroativa da renda ao mudar o salário do cargo atual,
        //  a próxima iteração do sistema deverá implementar uma entidade `Payroll` (Fechamento de
        //  Folha) para registrar o snapshot (foto) do salário-base exato no momento do cálculo, ou
        //  implementar vigência temporal na entidade `Position`.
        BigDecimal baseSalary = worker.getPosition() != null && worker.getPosition().getBaseSalary() != null
                ? worker.getPosition().getBaseSalary()
                : BigDecimal.ZERO;

        // Regra 2: afastado ou em férias recebe o salário-base, mas não horas extras
        BigDecimal contractsTotal = BigDecimal.ZERO;
        if (worker.getStatus() == WorkerStatus.ATIVO) {
            LocalDate start = LocalDate.of(year, month, 1);
            BigDecimal sum = hourContractRepository.sumContractsValue(workerId, start, start.plusMonths(1));
            contractsTotal = sum != null ? sum : BigDecimal.ZERO;
        }

        return new IncomeResponseDTO(
                worker.getName(),
                departmentName(worker),
                baseSalary,
                month,
                year,
                baseSalary.add(contractsTotal));
    }

    private Worker getWorker(Long id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado: id " + id));
    }

    private String departmentName(Worker worker) {
        Department department = worker.getDepartment();
        return department != null ? department.getName() : null;
    }

    private WorkerDTO toDTO(Worker worker) {
        return new WorkerDTO(worker.getId(), worker.getName(), departmentName(worker));
    }
}

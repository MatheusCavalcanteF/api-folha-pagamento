package com.payroll.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payroll.domain.Department;
import com.payroll.domain.HourContract;
import com.payroll.domain.Position;
import com.payroll.domain.Worker;
import com.payroll.domain.WorkerStatus;
import com.payroll.dto.IncomeResponseDTO;
import com.payroll.dto.WorkerDTO;
import com.payroll.infra.BusinessRuleException;
import com.payroll.infra.InvalidParameterException;
import com.payroll.infra.ResourceNotFoundException;
import com.payroll.repositories.DepartmentRepository;
import com.payroll.repositories.PositionRepository;
import com.payroll.repositories.WorkerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorkerServiceTest {

    @Autowired
    private WorkerService service;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private PositionRepository positionRepository;

    @Test
    void carregaSeedAnonimizado() {
        assertThat(workerRepository.count()).isEqualTo(351);
        assertThat(positionRepository.count()).isEqualTo(20);
        assertThat(departmentRepository.count()).isEqualTo(8);
    }

    @Test
    void paginaFuncionariosComDepartamento() {
        Page<WorkerDTO> page = service.findAll(PageRequest.of(0, 20, Sort.by("id")));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getTotalElements()).isEqualTo(351);
        assertThat(page.getTotalPages()).isEqualTo(18);
        assertThat(page.getContent()).extracting(WorkerDTO::departmentName).doesNotContainNull();
    }

    @Test
    void ultimaPaginaTemOResto() {
        Page<WorkerDTO> page = service.findAll(PageRequest.of(17, 20, Sort.by("id")));

        assertThat(page.getContent()).hasSize(11);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void ordenarPorDepartamentoNaoRepeteNemPulaFuncionariosEntrePaginas() {
        Set<Long> ids = new HashSet<>();
        for (int page = 0; page < 18; page++) {
            service.findAll(PageRequest.of(page, 20, Sort.by("department.name")))
                    .forEach(w -> ids.add(w.id()));
        }
        assertThat(ids).hasSize(351);
    }

    @Test
    void ordenacaoDesempataPorId() {
        Page<WorkerDTO> page = service.findAll(PageRequest.of(0, 50, Sort.by("department.name")));

        // Dentro do mesmo departamento, os ids vêm em ordem crescente
        String department = page.getContent().get(0).departmentName();
        assertThat(page.getContent().stream().filter(w -> w.departmentName().equals(department)).map(WorkerDTO::id))
                .isSorted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"naoExiste", "contracts.hours", "position.baseSalary", "registration"})
    void ordenacaoForaDaListaLancaInvalidParameter(String property) {
        assertThatThrownBy(() -> service.findAll(PageRequest.of(0, 20, Sort.by(property))))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Ordenação não suportada: " + property + " (use id, name ou department.name)");
    }

    @Test
    void buscaPorIdInexistenteLancaExcecao() {
        assertThatThrownBy(() -> service.findById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-3, 0, 1899, 2101, 1_000_000_000})
    void anoForaDoIntervaloLancaInvalidParameter(int year) {
        assertThatThrownBy(() -> service.calculateIncome(2L, year, 5))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessage("Ano inválido: " + year + " (use 1900 a 2100)");
    }

    @Test
    void anosNosLimitesSaoAceitos() {
        assertThat(service.calculateIncome(2L, 1900, 1).year()).isEqualTo(1900);
        assertThat(service.calculateIncome(2L, 2100, 12).year()).isEqualTo(2100);
    }

    @Test
    void mesInvalidoLancaIllegalArgument() {
        assertThatThrownBy(() -> service.calculateIncome(1L, 2026, 13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ativoSomaApenasContratosDoMesEAnoInformados() {
        Worker worker = newWorker("1000.00", WorkerStatus.ATIVO);
        addAugustAndNoiseContracts(worker);
        workerRepository.saveAndFlush(worker);

        IncomeResponseDTO dto = service.calculateIncome(worker.getId(), 2026, 8);

        assertThat(dto.baseSalary()).isEqualByComparingTo("1000.00");
        assertThat(dto.totalIncome()).isEqualByComparingTo("1091.00");
        assertThat(dto.month()).isEqualTo(8);
        assertThat(dto.year()).isEqualTo(2026);
    }

    @Test
    void ativoSemContratosNoMesRetornaSalarioBase() {
        Worker worker = newWorker("2500.00", WorkerStatus.ATIVO);
        workerRepository.saveAndFlush(worker);

        assertThat(service.calculateIncome(worker.getId(), 2026, 1).totalIncome()).isEqualByComparingTo("2500.00");
    }

    @Test
    void inativoLancaBusinessRuleException() {
        Worker worker = newWorker("1000.00", WorkerStatus.INATIVO);
        addAugustAndNoiseContracts(worker);
        workerRepository.saveAndFlush(worker);

        assertThatThrownBy(() -> service.calculateIncome(worker.getId(), 2026, 8))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível processar folha de pagamento para funcionário inativo");
    }

    @ParameterizedTest
    @EnumSource(value = WorkerStatus.class, names = {"AFASTADO", "FERIAS"})
    void afastadoOuFeriasRecebeSoSalarioBaseIgnorandoHorasExtras(WorkerStatus status) {
        Worker worker = newWorker("1000.00", status);
        addAugustAndNoiseContracts(worker);
        workerRepository.saveAndFlush(worker);

        IncomeResponseDTO dto = service.calculateIncome(worker.getId(), 2026, 8);

        assertThat(dto.baseSalary()).isEqualByComparingTo("1000.00");
        assertThat(dto.totalIncome()).isEqualByComparingTo("1000.00");
    }

    @Test
    void cargoSemSalarioContaComoZero() {
        Worker worker = newWorker("0", WorkerStatus.ATIVO);
        worker.getPosition().setBaseSalary(null);
        workerRepository.saveAndFlush(worker);

        assertThat(service.calculateIncome(worker.getId(), 2026, 1).totalIncome()).isEqualByComparingTo("0");
    }

    /** Dois contratos dentro de agosto/2026 (R$ 91,00) e três de ruído fora do mês. */
    private void addAugustAndNoiseContracts(Worker worker) {
        worker.getContracts().add(contract(worker, "2026-08-01", "10.00", 5));   // 50  (limite inicial)
        worker.getContracts().add(contract(worker, "2026-08-31", "20.50", 2));   // 41  (limite final)
        worker.getContracts().add(contract(worker, "2026-07-31", "99.00", 10));  // fora: mês anterior
        worker.getContracts().add(contract(worker, "2026-09-01", "99.00", 10));  // fora: mês seguinte
        worker.getContracts().add(contract(worker, "2025-08-15", "99.00", 10));  // fora: outro ano
    }

    private Worker newWorker(String salary, WorkerStatus status) {
        Department department = new Department();
        department.setName("Teste");
        departmentRepository.save(department);

        Position position = new Position();
        position.setTitle("Cargo teste");
        position.setBaseSalary(new BigDecimal(salary));
        positionRepository.save(position);

        Worker worker = new Worker();
        worker.setRegistration("TESTE-" + System.nanoTime());
        worker.setName("Fulano de Teste");
        worker.setStatus(status);
        worker.setDepartment(department);
        worker.setPosition(position);
        return worker;
    }

    private HourContract contract(Worker worker, String date, String valuePerHour, int hours) {
        HourContract c = new HourContract();
        c.setDate(LocalDate.parse(date));
        c.setValuePerHour(new BigDecimal(valuePerHour));
        c.setHours(hours);
        c.setWorker(worker);
        return c;
    }
}

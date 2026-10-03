package com.payroll.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payroll.domain.Department;
import com.payroll.domain.HourContract;
import com.payroll.domain.Position;
import com.payroll.domain.Worker;
import com.payroll.domain.WorkerStatus;
import com.payroll.repositories.DepartmentRepository;
import com.payroll.repositories.PositionRepository;
import com.payroll.repositories.WorkerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkerControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private PositionRepository positionRepository;

    @Test
    void listaPaginadaComTamanhoPadraoDe20() throws Exception {
        mockMvc.perform(get("/workers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(351))
                .andExpect(jsonPath("$.page.totalPages").value(18))
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void respeitaParametrosDePaginacao() throws Exception {
        mockMvc.perform(get("/workers").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.content[0].id").value(6));
    }

    @Test
    void limitaTamanhoMaximoDaPagina() throws Exception {
        mockMvc.perform(get("/workers").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void buscaPorId() throws Exception {
        mockMvc.perform(get("/workers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.departmentName").isNotEmpty());
    }

    @Test
    void idInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/workers/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rendaDeAtivoInclueHorasExtras() throws Exception {
        Worker worker = saveWorker(WorkerStatus.ATIVO);

        mockMvc.perform(income(worker, 8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerName").value("Fulano de Teste"))
                .andExpect(jsonPath("$.month").value(8))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.baseSalary").value(1000.00))
                .andExpect(jsonPath("$.totalIncome").value(1100.00));
    }

    @Test
    void rendaDeAfastadoNaoInclueHorasExtras() throws Exception {
        Worker worker = saveWorker(WorkerStatus.AFASTADO);

        mockMvc.perform(income(worker, 8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1000.00));
    }

    @Test
    void rendaDeInativoRetorna422() throws Exception {
        Worker worker = saveWorker(WorkerStatus.INATIVO);

        mockMvc.perform(income(worker, 8))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Não é possível processar folha de pagamento para funcionário inativo"));
    }

    @Test
    void mesInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/workers/1/income").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parametroAusenteRetorna400() throws Exception {
        mockMvc.perform(get("/workers/1/income").param("year", "2026"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder income(Worker w, int month) {
        return get("/workers/{id}/income", w.getId()).param("year", "2026").param("month", String.valueOf(month));
    }

    /** Salário base R$ 1.000,00 e um contrato de R$ 100,00 (10h x R$ 10,00) em agosto/2026. */
    private Worker saveWorker(WorkerStatus status) {
        Department department = new Department();
        department.setName("Teste");
        departmentRepository.save(department);

        Position position = new Position();
        position.setTitle("Cargo teste");
        position.setBaseSalary(new BigDecimal("1000.00"));
        positionRepository.save(position);

        Worker worker = new Worker();
        worker.setRegistration("CTRL-" + System.nanoTime());
        worker.setName("Fulano de Teste");
        worker.setStatus(status);
        worker.setDepartment(department);
        worker.setPosition(position);

        HourContract contract = new HourContract();
        contract.setDate(LocalDate.of(2026, 8, 10));
        contract.setValuePerHour(new BigDecimal("10.00"));
        contract.setHours(10);
        contract.setWorker(worker);
        worker.getContracts().add(contract);

        return workerRepository.saveAndFlush(worker);
    }
}

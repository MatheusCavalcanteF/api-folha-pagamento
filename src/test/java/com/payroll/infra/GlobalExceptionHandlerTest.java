package com.payroll.infra;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payroll.services.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class GlobalExceptionHandlerTest {

    private static final String DETALHE_INTERNO = "jdbc:h2:mem:payrolldb tabela WORKER coluna SECRET";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private WorkerService workerService;

    @Test
    void excecaoInesperadaRetorna500SemDetalheInterno() throws Exception {
        when(workerService.findById(anyLong())).thenThrow(new IllegalStateException(DETALHE_INTERNO));

        mockMvc.perform(get("/workers/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Erro interno do servidor."))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void illegalArgumentGenericoRetorna400SemExporMensagem() throws Exception {
        when(workerService.findById(anyLong())).thenThrow(new IllegalArgumentException(DETALHE_INTERNO));

        mockMvc.perform(get("/workers/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Requisição inválida."))
                .andExpect(content().string(not(containsString("jdbc"))));
    }

    @Test
    void erroDeParametroConhecidoExpoeAMensagem() throws Exception {
        when(workerService.calculateIncome(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new InvalidParameterException("Mês inválido: 13 (use 1 a 12)"));

        mockMvc.perform(get("/workers/1/income").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Mês inválido: 13 (use 1 a 12)"));
    }

    @Test
    void parametroComTipoInvalidoContinuaRetornando400() throws Exception {
        mockMvc.perform(get("/workers/1/income").param("year", "abc").param("month", "8"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parametroAusenteContinuaRetornando400() throws Exception {
        mockMvc.perform(get("/workers/1/income").param("year", "2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rotaInexistenteNaoViraErro500() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(status().isNotFound());
    }
}

package com.payroll.infra;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.security.user.name=analista",
        "spring.security.user.password=senha-de-teste"
})
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void semCredencialRetorna401() throws Exception {
        mockMvc.perform(get("/workers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")));
    }

    @Test
    void semCredencialNaRendaRetorna401() throws Exception {
        mockMvc.perform(get("/workers/1/income").param("year", "2026").param("month", "8"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaErradaRetorna401() throws Exception {
        mockMvc.perform(get("/workers").with(httpBasic("analista", "outra-senha")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void comCredencialValidaRetorna200() throws Exception {
        mockMvc.perform(get("/workers").with(httpBasic("analista", "senha-de-teste")))
                .andExpect(status().isOk());
    }

    @Test
    void naoCriaSessaoENaoEmiteCookie() throws Exception {
        mockMvc.perform(get("/workers").with(httpBasic("analista", "senha-de-teste")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void mantemCabecalhosDeSegurancaPadrao() throws Exception {
        mockMvc.perform(get("/workers").with(httpBasic("analista", "senha-de-teste")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}

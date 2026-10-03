# API de Folha de Pagamento Hospitalar

API REST em Java/Spring Boot que calcula a renda mensal de funcionários de um complexo hospitalar público, combinando salário-base do cargo, horas extras e plantões, e aplicando as regras do departamento pessoal conforme a situação de cada funcionário.

## O problema de negócio

Em um hospital, a folha vai além de salário fixo: boa parte da equipe (enfermagem, técnicos, administrativo de apoio) acumula **plantões e horas extras** (`HourContract`), e a situação funcional muda ao longo do ano. Calcular errado significa pagar quem não deve ou deixar de pagar quem tem direito.

A API trata isso com regras explícitas, validadas por testes:

| Situação (`WorkerStatus`) | Comportamento em `GET /workers/{id}/income` |
|---|---|
| `ATIVO` | Salário-base do cargo **mais** as horas extras/plantões do mês (`horas × valor/hora`) |
| `FERIAS` | Apenas o salário-base. Horas extras são ignoradas |
| `AFASTADO` | Apenas o salário-base. Horas extras são ignoradas |
| `INATIVO` | Recusado com **HTTP 422**: não se processa folha de funcionário inativo, o que previne pagamentos indevidos |

A estrutura (8 departamentos, 20 cargos, 351 funcionários) reproduz a de uma unidade hospitalar real, a partir de dados públicos de transparência. **Todos os nomes e matrículas do seed são fictícios**; só cargos, faixas salariais e departamentos refletem a estrutura original.

## Stack

- Java 17+ (testado em JDK 25)
- Spring Boot 3.5 (Web, Validation)
- Spring Data JPA / Hibernate
- H2 (em memória, profile `dev`)
- Flyway (fundação de migrations para produção)
- Lombok
- JUnit 5, AssertJ e MockMvc
- Maven (com Maven Wrapper)

## Arquitetura

```
com.payroll
├── controllers   # Camada HTTP (WorkerController)
├── services      # Regras de negócio (WorkerService)
├── repositories  # Spring Data JPA + JPQL
├── domain        # Entidades: Department, Position, Worker, HourContract, WorkerStatus
├── dto           # Records de entrada/saída (WorkerDTO, IncomeResponseDTO)
└── infra         # Exceções, handler global e configuração web
```

### Decisões arquiteturais

- **Erros semânticos com status HTTP corretos.** Violação de regra de negócio lança `BusinessRuleException` e vira **422 Unprocessable Entity**; recurso inexistente vira **404**; parâmetro inválido (ex.: mês 13) vira **400**. Tudo centralizado em um `@RestControllerAdvice` com `ProblemDetail` (RFC 9457), sem `try/catch` nos controllers.
- **Cálculo financeiro no banco.** A soma `SUM(hours * valuePerHour)` roda em uma query JPQL no `HourContractRepository`, em vez de carregar os contratos do funcionário e somar em memória. O filtro de mês usa intervalo semiaberto (`date >= 1º do mês AND date < 1º do mês seguinte`), que cobre o mês inteiro e permite uso de índice. Para `FERIAS` e `AFASTADO` a query nem é executada.
- **Paginação controlada.** `GET /workers` é paginado (padrão de 20, ordenado por `id`) e o tamanho máximo é limitado a 100 em um `PageableHandlerMethodArgumentResolverCustomizer` (`WebConfig`), protegendo contra `?size=100000`. A resposta usa serialização estável (`content` + `page{...}`) via `VIA_DTO`.
- **Sem N+1.** `@EntityGraph` carrega departamento (e cargo) junto com o funcionário.
- **Precisão monetária.** `BigDecimal` com `precision = 12, scale = 2`.
- **Cargo como entidade.** `Position` guarda título e salário-base, então um cargo novo é um dado e não uma mudança de código.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/workers?page=0&size=20&sort=name` | Lista paginada de funcionários |
| `GET` | `/workers/{id}` | Busca um funcionário (404 se não existir) |
| `GET` | `/workers/{id}/income?year=2026&month=8` | Renda do mês (200, 400, 404 ou 422) |

Exemplo de resposta de `/workers/2/income?year=2026&month=5` (funcionário ativo com horas extras no mês):

```json
{
  "workerName": "André Barbosa Silva",
  "departmentName": "HOSPITAL EXEMPLO – ENFERMARIA",
  "baseSalary": 2207.10,
  "month": 5,
  "year": 2026,
  "totalIncome": 2427.78
}
```

## Como rodar

Pré-requisito: JDK 17 ou superior. O Maven não precisa estar instalado, pois o projeto traz o wrapper.

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

A aplicação sobe em `http://localhost:8080` com o profile `dev`, e o seed (`import.sql`) é carregado automaticamente.

```bash
curl "http://localhost:8080/workers?size=5"
curl "http://localhost:8080/workers/2/income?year=2026&month=5"
```

Console do H2: `http://localhost:8080/h2-console`, com JDBC URL `jdbc:h2:mem:payrolldb`, usuário `sa` e senha vazia.

### Testes

```bash
./mvnw clean install
```

Os testes de serviço e de integração (MockMvc) cobrem cada regra de status, os limites do filtro de mês (primeiro e último dia, mês vizinho e outro ano), a paginação e os códigos HTTP.

## Estado atual e próximos passos

Este é um projeto de portfólio em evolução. Pontos conhecidos e deliberados:

- **Banco e migrations.** O profile `dev` usa H2 em memória com `ddl-auto=create-drop` e `import.sql`. O Flyway já está no classpath com uma migration baseline vazia (`V1__Initial_Setup.sql`); o schema de produção virá em migrations versionadas.
- **Histórico salarial.** O salário-base é lido do cargo atual, então alterar o salário de um cargo mudaria retroativamente a renda de meses já calculados. A evolução prevista é uma entidade `Payroll` (fechamento de folha) com snapshot do salário no momento do cálculo, ou vigência temporal em `Position`.
- **Segurança.** Não há autenticação nem autorização ainda, e o `/h2-console` está habilitado no profile `dev`. Isso precisa mudar antes de qualquer deploy.
- **Escrita.** A API é somente leitura por enquanto (sem cadastro de funcionários ou contratos).

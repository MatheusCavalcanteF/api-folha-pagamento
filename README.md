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

A estrutura (8 departamentos, 20 cargos, 351 funcionários) é a de uma unidade hospitalar genérica ("Hospital Exemplo"). **Todos os nomes e matrículas do seed são fictícios**; só cargos, faixas salariais e departamentos seguem a estrutura de uma unidade pública, sem identificar nenhuma instituição ou pessoa real.

## Stack

- Java 17+ (CI em 17 e 21; testado também em JDK 25)
- Spring Boot 3.5 (Web, Validation, Security)
- Spring Data JPA / Hibernate
- H2 (em memória, profile `dev`)
- Flyway (fundação de migrations para produção)
- Lombok
- JUnit 5, AssertJ, MockMvc e Spring Security Test
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

- **Erros semânticos com status HTTP corretos.** Violação de regra de negócio lança `BusinessRuleException` e vira **422 Unprocessable Entity**; recurso inexistente vira **404**; parâmetro inválido (ex.: mês 13) vira **400**; erro inesperado vira **500** com mensagem fixa, sem expor detalhe interno (o erro completo vai só para o log). Tudo centralizado em um `@RestControllerAdvice` com `ProblemDetail` (RFC 9457), sem `try/catch` nos controllers.
- **Cálculo financeiro no banco.** A soma `SUM(hours * valuePerHour)` roda em uma query JPQL no `HourContractRepository`, em vez de carregar os contratos do funcionário e somar em memória. O filtro de mês usa intervalo semiaberto (`date >= 1º do mês AND date < 1º do mês seguinte`), que cobre o mês inteiro e permite uso de índice. Para `FERIAS` e `AFASTADO` a query nem é executada.
- **Paginação controlada.** `GET /workers` é paginado (padrão de 20, ordenado por `id`) e o tamanho máximo é limitado a 100 em um `PageableHandlerMethodArgumentResolverCustomizer` (`WebConfig`), protegendo contra `?size=100000`. A resposta usa serialização estável (`content` + `page{...}`) via `VIA_DTO`.
- **Sem N+1.** `@EntityGraph` carrega departamento (e cargo) junto com o funcionário.
- **Precisão monetária.** `BigDecimal` com `precision = 12, scale = 2`.
- **Cargo como entidade.** `Position` guarda título e salário-base, então um cargo novo é um dado e não uma mudança de código.

## Endpoints

Todas exigem HTTP Basic (veja [Autenticação](#autenticação)).

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

A aplicação sobe em `http://127.0.0.1:8080` (só a própria máquina enxerga a porta) com o profile `dev`, e o seed (`import.sql`) é carregado automaticamente.

### Autenticação

Todas as rotas exigem **HTTP Basic**. As credenciais vêm de variáveis de ambiente:

| Variável | Padrão | Descrição |
|---|---|---|
| `APP_USER` | `analista` | Nome do usuário |
| `APP_PASSWORD` | *(sem padrão)* | Senha. Se não existir, o Spring Boot gera uma senha aleatória e a imprime no log de inicialização (`Using generated security password`) |

```bash
# Linux / macOS
export APP_PASSWORD='defina-uma-senha'
./mvnw spring-boot:run

# Windows (PowerShell)
$env:APP_PASSWORD = 'defina-uma-senha'
.\mvnw.cmd spring-boot:run
```

```bash
curl -u analista:defina-uma-senha "http://127.0.0.1:8080/workers?size=5"
curl -u analista:defina-uma-senha "http://127.0.0.1:8080/workers/2/income?year=2026&month=5"
```

Sem credencial (ou com senha errada) a resposta é `401`.

O console do H2 vem **desligado**. Para usá-lo localmente, veja o comentário em `application-dev.properties`.

### Testes

```bash
./mvnw verify
```

Os testes de serviço e de integração (MockMvc) cobrem cada regra de status, os limites do filtro de mês (primeiro e último dia, mês vizinho e outro ano), a paginação e os códigos HTTP.

## Decisões de segurança

| Decisão | Motivo |
|---|---|
| **HTTP Basic, stateless**, todas as rotas autenticadas | A API devolve dados de folha. Sem sessão no servidor, cada requisição se autentica sozinha. Usuário e senha vêm de `APP_USER` / `APP_PASSWORD`, sem senha padrão no código |
| **CSRF desabilitado** | A API é stateless, não usa cookie de sessão e todas as rotas são `GET`, sem alterar estado. Antes de criar endpoints de escrita, isso precisa ser revisto, porque o navegador pode reenviar credenciais HTTP Basic sozinho. Detalhes em [docs/SECURITY.md](docs/SECURITY.md#csrf) |
| **Erros em `ProblemDetail` sem vazamento** | Exceção inesperada vira `500` com mensagem fixa; o stack trace fica só no log do servidor. A mensagem de um `IllegalArgumentException` só aparece quando vem de um erro de parâmetro que a aplicação conhece |
| **Teto de paginação (100)** | Evita `?size=100000` despejando a base inteira em uma resposta |
| **H2 console desligado, servidor em `127.0.0.1`, SQL fora do log** | O profile padrão é `dev`, então o padrão precisa ser o seguro. O SQL só aparece se o logger `org.hibernate.SQL` for ligado em `debug` |
| **Cabeçalhos de segurança padrão do Spring Security** | `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control` e afins ficam ativos |
| **Dados fictícios** | Nomes e matrículas do seed são gerados; nenhuma planilha original é versionada (`*.xlsx`, `*.csv` ficam no `.gitignore`). Veja [docs/SECURITY.md](docs/SECURITY.md) |

## Limitações conhecidas

- **Sem rate limit.** Nada impede tentativas repetidas de senha. Em produção, limite no proxy reverso ou no gateway.
- **Sem TLS no app.** HTTP Basic envia a credencial em texto reversível (Base64); use um proxy reverso com HTTPS em produção e nunca exponha a porta direto.
- **Usuário único, sem papéis.** Quem se autentica lê todos os funcionários.
- **H2 em memória.** Os dados somem a cada reinício, e o `ddl-auto=create-drop` recria o schema.
- **Schema de produção ainda não definido.** O Flyway tem só a migration baseline vazia.

## Estado atual e próximos passos

Este é um projeto de portfólio em evolução. Pontos conhecidos e deliberados:

- **Banco e migrations.** O profile `dev` usa H2 em memória com `ddl-auto=create-drop` e `import.sql`. O Flyway já está no classpath com uma migration baseline vazia (`V1__Initial_Setup.sql`); o schema de produção virá em migrations versionadas.
- **Histórico salarial.** O salário-base é lido do cargo atual, então alterar o salário de um cargo mudaria retroativamente a renda de meses já calculados. A evolução prevista é uma entidade `Payroll` (fechamento de folha) com snapshot do salário no momento do cálculo, ou vigência temporal em `Position`.
- **Autorização.** Há autenticação (usuário único, HTTP Basic), mas não há papéis nem perfis de acesso por departamento. Veja [Limitações conhecidas](#limitações-conhecidas).
- **Escrita.** A API é somente leitura por enquanto (sem cadastro de funcionários ou contratos).

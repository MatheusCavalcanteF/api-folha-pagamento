# Política de segurança

## Como reportar uma vulnerabilidade

Use o **relato privado de vulnerabilidades** do GitHub: na aba **Security** do repositório, clique em **Report a vulnerability**. O relato fica visível só para quem mantém o projeto.

Não abra issue pública nem poste detalhes de exploração em PR. Informe a versão (commit), os passos para reproduzir e o impacto que você enxerga.

## Escopo

Dentro do escopo:

- autenticação e autorização da API (HTTP Basic);
- vazamento de informação em respostas de erro ou em logs;
- injeção (SQL/JPQL), validação de parâmetros e paginação;
- dependências com vulnerabilidade conhecida e configuração do CI.

Fora do escopo:

- ausência de rate limit e de TLS no próprio app, que estão em [Limitações conhecidas](../README.md#limitações-conhecidas) e devem ser resolvidas na camada de infraestrutura (proxy reverso com HTTPS);
- ataques que exigem acesso físico ou à máquina de quem executa o projeto;
- o console do H2, que vem desligado e só deve ser ligado localmente.

Este é um projeto de portfólio, sem SLA de correção.

## Dados e LGPD

- O repositório contém **somente dados fictícios**: nomes e matrículas dos 351 funcionários do seed são gerados.
- Cargos e faixas salariais seguem a estrutura de uma unidade pública genérica ("Hospital Exemplo"). Nenhuma instituição ou pessoa real é identificada.
- Nenhuma planilha original é versionada: `*.xlsx`, `*.xls` e `*.csv` estão no `.gitignore`.
- **Nunca suba dados reais de folha de pagamento** (nomes, matrículas, CPF, salários individuais) para este repositório, nem em issues, PRs, logs ou capturas de tela. Dados de folha são dados pessoais e, em parte, sensíveis para fins da LGPD. Se isso acontecer, avise pelo canal acima para que o histórico seja reescrito.

package com.payroll.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Estende ResponseEntityExceptionHandler para que as exceções do próprio Spring MVC (parâmetro ausente,
// tipo inválido, rota inexistente...) continuem com o status correto e não caiam no handler genérico de 500.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // Mensagem exposta apenas para erros de parâmetro que a própria aplicação lança.
    @ExceptionHandler(InvalidParameterException.class)
    public ProblemDetail handleInvalidParameter(InvalidParameterException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Qualquer outro IllegalArgumentException pode vir de biblioteca e ter detalhe interno na mensagem.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Requisição rejeitada por IllegalArgumentException", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisição inválida.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor.");
    }
}

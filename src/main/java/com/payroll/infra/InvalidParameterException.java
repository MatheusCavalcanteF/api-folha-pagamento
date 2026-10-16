package com.payroll.infra;

/**
 * Erro de parâmetro conhecido e controlado pela aplicação; a mensagem é segura para o cliente.
 * Estende IllegalArgumentException para manter o contrato de quem já a tratava.
 */
public class InvalidParameterException extends IllegalArgumentException {

    public InvalidParameterException(String message) {
        super(message);
    }
}

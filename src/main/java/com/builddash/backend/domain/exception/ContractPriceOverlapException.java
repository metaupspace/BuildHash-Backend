package com.builddash.backend.domain.exception;

public class ContractPriceOverlapException extends BadRequestException {

    public ContractPriceOverlapException(String message) {
        super("CONTRACT_PRICE_OVERLAP", message);
    }
}

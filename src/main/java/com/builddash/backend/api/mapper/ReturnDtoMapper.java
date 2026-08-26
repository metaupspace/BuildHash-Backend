package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.RefundSummaryResponse;
import com.builddash.backend.api.dto.response.ReturnLineItemResponse;
import com.builddash.backend.api.dto.response.ReturnResponse;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ReturnDtoMapper {

    public ReturnResponse toResponse(Return returnObj, Refund refund) {
        if (returnObj == null) {
            return null;
        }

        List<ReturnLineItemResponse> lineItemResponses = returnObj.lineItems() != null
                ? returnObj.lineItems().stream().map(this::toLineItemResponse).toList()
                : Collections.emptyList();

        RefundSummaryResponse refundSummary = refund != null
                ? new RefundSummaryResponse(refund.amount(), refund.status(), refund.gatewayRefundId())
                : null;

        return new ReturnResponse(
                returnObj.id(),
                returnObj.orderId(),
                returnObj.status(),
                returnObj.reason(),
                returnObj.photoKeys() != null ? returnObj.photoKeys() : Collections.emptyList(),
                lineItemResponses,
                refundSummary,
                returnObj.createdAt(),
                returnObj.updatedAt()
        );
    }

    public ReturnLineItemResponse toLineItemResponse(ReturnLineItem item) {
        if (item == null) {
            return null;
        }
        return new ReturnLineItemResponse(
                item.id(),
                item.productId(),
                item.quantityRequested(),
                item.refundAmount()
        );
    }
}

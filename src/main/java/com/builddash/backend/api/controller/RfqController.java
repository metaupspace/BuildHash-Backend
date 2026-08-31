package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.RfqConvertRequest;
import com.builddash.backend.api.dto.request.RfqCreateRequest;
import com.builddash.backend.api.dto.response.RfqConvertResponse;
import com.builddash.backend.api.dto.response.RfqQuoteResponse;
import com.builddash.backend.api.dto.response.RfqResponse;
import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Thin by design: authenticate, bind, delegate, respond. Every authorization
 * decision lives in RfqServiceImpl via B2bAuthorizer — no permission branching
 * here, and no caller-supplied company claim is ever trusted.
 */
@RestController
@RequestMapping("/rfq")
@Tag(name = "RFQs", description = "B2B request-for-quotation lifecycle")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RfqController {

    private final RfqService rfqService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an RFQ with creation-time vendor routing (RFQ_CREATE)")
    public RfqResponse create(@Valid @RequestBody RfqCreateRequest request,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        return RfqResponse.from(rfqService.create(
                user.userId(),
                request.companyId(),
                request.expiresAt(),
                request.notes(),
                request.items().stream()
                        .map(item -> new RfqService.ItemCommand(item.productId(), item.quantity()))
                        .toList()));
    }

    @GetMapping("/{rfqId}")
    @Operation(summary = "Get an RFQ (RFQ_VIEW, company scoped)")
    public RfqResponse get(@PathVariable UUID rfqId,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        return RfqResponse.from(rfqService.get(user.userId(), rfqId));
    }

    @GetMapping("/{rfqId}/quotes")
    @Operation(summary = "Compare quotes ordered by total ascending (QUOTE_VIEW)")
    public List<RfqQuoteResponse> listQuotes(@PathVariable UUID rfqId,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return rfqService.listQuotes(user.userId(), rfqId).stream()
                .map(RfqQuoteResponse::from)
                .toList();
    }

    @PostMapping("/{rfqId}/cancel")
    @Operation(summary = "Cancel an OPEN RFQ (RFQ_CANCEL)")
    public RfqResponse cancel(@PathVariable UUID rfqId,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        return RfqResponse.from(rfqService.cancel(user.userId(), rfqId));
    }

    @PostMapping("/{rfqId}/convert")
    @Operation(summary = "Convert a selected quote into a B2B draft cart (RFQ_CONVERT)")
    public RfqConvertResponse convert(@PathVariable UUID rfqId,
                                      @Valid @RequestBody RfqConvertRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return RfqConvertResponse.from(rfqService.convert(user.userId(), rfqId, request.quoteId()));
    }
}

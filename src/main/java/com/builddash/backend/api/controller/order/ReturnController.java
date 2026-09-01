package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.CreateReturnRequest;
import com.builddash.backend.api.dto.response.ReturnResponse;
import com.builddash.backend.api.mapper.ReturnDtoMapper;
import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Returns", description = "Return and Refund management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;
    private final ReturnDtoMapper returnDtoMapper;

    @PostMapping(value = "/orders/{id}/return", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a return request for a delivered order with photos")
    public ReturnResponse createReturn(
            @PathVariable("id") UUID orderId,
            @Valid @RequestPart("request") CreateReturnRequest request,
            @RequestPart("photos") List<MultipartFile> photos,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Return returnObj = returnService.createReturn(
                user.userId(),
                orderId,
                request.reason(),
                request.lineItems(),
                photos
        );

        return returnDtoMapper.toResponse(returnObj, null);
    }

    @GetMapping("/returns/{id}")
    @Operation(summary = "Get details of a return request")
    public ReturnResponse getReturn(
            @PathVariable("id") UUID returnId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Return returnObj = returnService.getReturn(user.userId(), user.roles(), returnId);
        Refund refund = returnService.getRefund(returnId).orElse(null);
        return returnDtoMapper.toResponse(returnObj, refund);
    }

    @PostMapping("/returns/{id}/reject")
    @Operation(summary = "Vendor or Admin rejects a return request")
    public ReturnResponse rejectReturn(
            @PathVariable("id") UUID returnId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Return rejected = returnService.reject(returnId, user.userId(), user.roles());
        Refund refund = returnService.getRefund(returnId).orElse(null);
        return returnDtoMapper.toResponse(rejected, refund);
    }

    @PostMapping("/returns/{id}/qc-pass")
    @Operation(summary = "Vendor or Admin marks QC passed and initiates refund")
    public ReturnResponse passQc(
            @PathVariable("id") UUID returnId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Return inRefund = returnService.passQc(returnId, user.userId(), user.roles());
        Refund refund = returnService.getRefund(returnId).orElse(null);
        return returnDtoMapper.toResponse(inRefund, refund);
    }
}

package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.VendorCreateRequest;
import com.builddash.backend.api.dto.request.VendorUpdateRequest;
import com.builddash.backend.api.dto.response.VendorResponse;
import com.builddash.backend.application.service.VendorAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Application-ADMIN territory: ROLE_ADMIN is enforced by SecurityConfig on
 * /admin/** — no B2B permission applies and no company membership helps.
 */
@RestController
@RequestMapping("/admin/vendors")
@Tag(name = "Vendor administration", description = "Application-admin vendor management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VendorAdminController {

    private final VendorAdminService vendorAdminService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a vendor with its category mapping")
    public VendorResponse create(@Valid @RequestBody VendorCreateRequest request) {
        return VendorResponse.from(vendorAdminService.create(request.name(), request.categoryIds()));
    }

    @GetMapping
    @Operation(summary = "List all vendors")
    public List<VendorResponse> list() {
        return vendorAdminService.list().stream().map(VendorResponse::from).toList();
    }

    @PatchMapping("/{vendorId}")
    @Operation(summary = "Update vendor name/categories/active (historical routing untouched)")
    public VendorResponse update(@PathVariable UUID vendorId,
                                 @Valid @RequestBody VendorUpdateRequest request) {
        return VendorResponse.from(vendorAdminService.update(
                vendorId, request.name(), request.categoryIds(), request.active()));
    }
}

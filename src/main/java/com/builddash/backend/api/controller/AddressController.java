package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.CreateAddressRequest;
import com.builddash.backend.api.dto.response.AddressResponse;
import com.builddash.backend.api.mapper.AddressDtoMapper;
import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.model.Address;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/addresses")
@Tag(name = "Addresses", description = "Manage user delivery addresses and geocoding")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final AddressDtoMapper addressDtoMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a new delivery address")
    public AddressResponse createAddress(@Valid @RequestBody CreateAddressRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        Address address = addressService.createAddress(
                user.userId(),
                request.type(),
                request.line1(),
                request.line2(),
                request.city(),
                request.state(),
                request.zipCode()
        );
        return addressDtoMapper.toResponse(address);
    }

    @GetMapping
    @Operation(summary = "List all addresses for current user")
    public List<AddressResponse> getAddresses(@AuthenticationPrincipal AuthenticatedUser user) {
        return addressDtoMapper.toResponseList(addressService.getAddresses(user.userId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single address by ID")
    public AddressResponse getAddress(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        Address address = addressService.getAddress(id, user.userId());
        return addressDtoMapper.toResponse(address);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an address")
    public void deleteAddress(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        addressService.deleteAddress(id, user.userId());
    }
}

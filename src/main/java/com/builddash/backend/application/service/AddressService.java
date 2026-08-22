package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Address;

import java.util.List;
import java.util.UUID;

public interface AddressService {
    Address createAddress(UUID userId, String type, String line1, String line2, String city, String state, String zipCode);
    List<Address> getAddresses(UUID userId);
    Address getAddress(UUID id);
    void deleteAddress(UUID id, UUID userId);
}

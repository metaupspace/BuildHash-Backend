package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.GeocodingGateway;
import com.builddash.backend.domain.port.ServiceabilityGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final GeocodingGateway geocodingGateway;
    private final ServiceabilityGateway serviceabilityGateway;

    @Override
    @Transactional
    public Address createAddress(UUID userId, String type, String line1, String line2, String city, String state, String zipCode) {
        Optional<GeocodingGateway.Coordinates> coords = geocodingGateway.geocode(line1, line2, city, state, zipCode);
        
        Double lat = coords.map(GeocodingGateway.Coordinates::lat).orElse(null);
        Double lng = coords.map(GeocodingGateway.Coordinates::lng).orElse(null);
        
        boolean serviceable = false;
        if (lat != null && lng != null) {
            serviceable = serviceabilityGateway.isServiceable(lat, lng);
        }

        Address address = new Address(
                UUID.randomUUID(),
                userId,
                type,
                line1,
                line2,
                city,
                state,
                zipCode,
                lat,
                lng,
                serviceable
        );
        return addressRepository.save(address);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> getAddresses(UUID userId) {
        return addressRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Address getAddress(UUID id) {
        return addressRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "Address not found"));
    }

    @Override
    @Transactional
    public void deleteAddress(UUID id, UUID userId) {
        Address address = getAddress(id);
        if (!address.userId().equals(userId)) {
            throw new UnauthorizedException("ACCESS_DENIED", "Cannot delete an address belonging to another user");
        }
        addressRepository.deleteById(id);
    }
}

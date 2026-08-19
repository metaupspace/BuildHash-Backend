package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.GeocodingGateway;
import com.builddash.backend.domain.port.ServiceabilityGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressServiceTest {

    private AddressRepository addressRepository;
    private GeocodingGateway geocodingGateway;
    private ServiceabilityGateway serviceabilityGateway;
    private AddressService addressService;

    @BeforeEach
    void setUp() {
        addressRepository = mock(AddressRepository.class);
        geocodingGateway = mock(GeocodingGateway.class);
        serviceabilityGateway = mock(ServiceabilityGateway.class);
        addressService = new AddressServiceImpl(addressRepository, geocodingGateway, serviceabilityGateway);
    }

    @Test
    void createAddress_geocodesAndChecksServiceability_thenSaves() {
        UUID userId = UUID.randomUUID();
        when(geocodingGateway.geocode(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new GeocodingGateway.Coordinates(12.34, 56.78)));
        when(serviceabilityGateway.isServiceable(12.34, 56.78)).thenReturn(true);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        Address address = addressService.createAddress(userId, "HOME", "123 Main St", null, "City", "State", "12345");

        assertThat(address.lat()).isEqualTo(12.34);
        assertThat(address.lng()).isEqualTo(56.78);
        assertThat(address.isServiceable()).isTrue();
        assertThat(address.userId()).isEqualTo(userId);
        
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void createAddress_whenGeocodingFails_savesWithNullCoordinatesAndNotServiceable() {
        UUID userId = UUID.randomUUID();
        when(geocodingGateway.geocode(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        Address address = addressService.createAddress(userId, "SITE", "123 Main St", null, "City", "State", "12345");

        assertThat(address.lat()).isNull();
        assertThat(address.lng()).isNull();
        assertThat(address.isServiceable()).isFalse();
    }

    @Test
    void getAddresses_returnsRepositoryResults() {
        UUID userId = UUID.randomUUID();
        Address addr = new Address(UUID.randomUUID(), userId, "HOME", "A", null, "B", "C", "D", 0.0, 0.0, true);
        when(addressRepository.findByUserId(userId)).thenReturn(List.of(addr));

        List<Address> result = addressService.getAddresses(userId);
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(addr);
    }

    @Test
    void getAddress_whenNotFound_throwsException() {
        when(addressRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Address not found");
    }

    @Test
    void deleteAddress_whenOwnAddress_deletes() {
        UUID userId = UUID.randomUUID();
        UUID addrId = UUID.randomUUID();
        Address addr = new Address(addrId, userId, "HOME", "A", null, "B", "C", "D", 0.0, 0.0, true);
        when(addressRepository.findById(addrId)).thenReturn(Optional.of(addr));

        addressService.deleteAddress(addrId, userId);

        verify(addressRepository).deleteById(addrId);
    }

    @Test
    void deleteAddress_whenAnotherUsersAddress_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID addrId = UUID.randomUUID();
        Address addr = new Address(addrId, otherUserId, "HOME", "A", null, "B", "C", "D", 0.0, 0.0, true);
        when(addressRepository.findById(addrId)).thenReturn(Optional.of(addr));

        assertThatThrownBy(() -> addressService.deleteAddress(addrId, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Cannot delete an address belonging to another user");
    }
}

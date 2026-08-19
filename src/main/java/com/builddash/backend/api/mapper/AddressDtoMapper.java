package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.AddressResponse;
import com.builddash.backend.domain.model.Address;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddressDtoMapper {

    public AddressResponse toResponse(Address address) {
        if (address == null) return null;
        return new AddressResponse(
                address.id(),
                address.userId(),
                address.type(),
                address.line1(),
                address.line2(),
                address.city(),
                address.state(),
                address.zipCode(),
                address.lat(),
                address.lng(),
                address.isServiceable()
        );
    }

    public List<AddressResponse> toResponseList(List<Address> addresses) {
        if (addresses == null) return List.of();
        return addresses.stream().map(this::toResponse).toList();
    }
}

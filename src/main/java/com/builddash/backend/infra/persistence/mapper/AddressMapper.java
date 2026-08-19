package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Address;
import com.builddash.backend.infra.persistence.entity.AddressEntity;

public class AddressMapper {

    private AddressMapper() {}

    public static Address toDomain(AddressEntity entity) {
        if (entity == null) return null;
        return new Address(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getLine1(),
                entity.getLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getZipCode(),
                entity.getLat(),
                entity.getLng(),
                entity.isServiceable()
        );
    }

    public static AddressEntity toEntity(Address address) {
        if (address == null) return null;
        AddressEntity entity = new AddressEntity();
        entity.setId(address.id());
        entity.setUserId(address.userId());
        entity.setType(address.type());
        entity.setLine1(address.line1());
        entity.setLine2(address.line2());
        entity.setCity(address.city());
        entity.setState(address.state());
        entity.setZipCode(address.zipCode());
        entity.setLat(address.lat());
        entity.setLng(address.lng());
        entity.setServiceable(address.isServiceable());
        return entity;
    }
}

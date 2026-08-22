package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AddressRepositoryAdapterJpaIT extends AbstractIntegrationTest {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFind_persistsAddressCorrectly() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setPhone("+1234567890");
        User user = userRepository.save(u);

        Address address = new Address(
                UUID.randomUUID(),
                user.getId(),
                "HOME",
                "123 Street",
                "Apt 4",
                "City",
                "State",
                "12345",
                12.34,
                56.78,
                true
        );

        Address saved = addressRepository.save(address);
        assertThat(saved.id()).isEqualTo(address.id());

        Optional<Address> found = addressRepository.findById(address.id());
        assertThat(found).isPresent();
        assertThat(found.get().line1()).isEqualTo("123 Street");
        assertThat(found.get().isServiceable()).isTrue();
    }

    @Test
    void findByUserId_returnsAddressesForUser() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setPhone("+1234567891");
        User user = userRepository.save(u);
        User ou = new User();
        ou.setId(UUID.randomUUID());
        ou.setPhone("+1234567892");
        User otherUser = userRepository.save(ou);

        addressRepository.save(new Address(UUID.randomUUID(), user.getId(), "HOME", "A", null, "B", "C", "D", null, null, false));
        addressRepository.save(new Address(UUID.randomUUID(), user.getId(), "SITE", "X", null, "Y", "Z", "W", null, null, false));
        addressRepository.save(new Address(UUID.randomUUID(), otherUser.getId(), "WORK", "1", null, "2", "3", "4", null, null, false));

        List<Address> addresses = addressRepository.findByUserId(user.getId());
        assertThat(addresses).hasSize(2);
        assertThat(addresses).extracting(Address::type).containsExactlyInAnyOrder("HOME", "SITE");
    }

    @Test
    void deleteById_removesAddress() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setPhone("+1234567893");
        User user = userRepository.save(u);
        Address address = addressRepository.save(new Address(UUID.randomUUID(), user.getId(), "HOME", "A", null, "B", "C", "D", null, null, false));

        assertThat(addressRepository.findById(address.id())).isPresent();

        addressRepository.deleteById(address.id());

        assertThat(addressRepository.findById(address.id())).isEmpty();
    }
}

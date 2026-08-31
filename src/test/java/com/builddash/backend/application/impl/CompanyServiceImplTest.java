package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @InjectMocks
    private CompanyServiceImpl service;

    private final UUID creator = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @Test
    void create_savesCompanyAndCreatorOwnerMembership() {
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(companyMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company created = service.create(creator, "Acme", "27AAAPZ1234C1ZV", "a@b.c", null);

        assertThat(created.businessTimezone()).isEqualTo("Asia/Kolkata"); // default
        ArgumentCaptor<CompanyMember> memberCaptor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().userId()).isEqualTo(creator);
        assertThat(memberCaptor.getValue().role()).isEqualTo(CompanyRole.OWNER);
        assertThat(memberCaptor.getValue().companyId()).isEqualTo(created.id());
    }

    @Test
    void get_nonMemberToken_gets404() {
        assertThatThrownBy(() -> service.get(companyId, List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_memberReadsCompany() {
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(new Company(companyId, "Acme", null, null, "Asia/Kolkata",
                        CompanyStatus.ACTIVE, null, null)));

        Company result = service.get(companyId, List.of(new B2bMembership(companyId, CompanyRole.BUYER, List.of())));
        assertThat(result.id()).isEqualTo(companyId);
    }

    @Test
    void update_buyerRoleGets403() {
        UUID actor = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actor))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), companyId, actor,
                        CompanyRole.BUYER, null, null)));

        assertThatThrownBy(() -> service.update(companyId, actor,
                List.of(new B2bMembership(companyId, CompanyRole.BUYER, List.of())),
                "New", null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_adminAllowed_revokedDbRoleStillBlocks_staleTokenCannotElevate() {
        // Token says ADMIN (stale), DB says BUYER (revoked) — DB wins (decision 4)
        UUID actor = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actor))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), companyId, actor,
                        CompanyRole.BUYER, null, null)));

        assertThatThrownBy(() -> service.update(companyId, actor,
                List.of(new B2bMembership(companyId, CompanyRole.ADMIN, List.of())),
                "New", null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_adminSucceeds() {
        UUID actor = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actor))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), companyId, actor,
                        CompanyRole.ADMIN, null, null)));
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(new Company(companyId, "Acme", null, null, "Asia/Kolkata",
                        CompanyStatus.ACTIVE, null, null)));
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company updated = service.update(companyId, actor,
                List.of(new B2bMembership(companyId, CompanyRole.ADMIN, List.of())),
                "Renamed", "27G", "x@y.z", "UTC");

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.businessTimezone()).isEqualTo("UTC");
    }

    @Test
    void updateStatus_suspendsCompany() {
        UUID actor = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actor))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), companyId, actor,
                        CompanyRole.OWNER, null, null)));
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(new Company(companyId, "Acme", null, null, "Asia/Kolkata",
                        CompanyStatus.ACTIVE, null, null)));
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company suspended = service.updateStatus(companyId, actor,
                List.of(new B2bMembership(companyId, CompanyRole.OWNER, List.of())),
                CompanyStatus.SUSPENDED);
        assertThat(suspended.status()).isEqualTo(CompanyStatus.SUSPENDED);
    }
}

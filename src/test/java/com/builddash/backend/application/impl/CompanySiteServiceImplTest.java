package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SiteInUseException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.domain.port.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySiteServiceImplTest {

    @Mock
    private CompanySiteRepository companySiteRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CompanySiteServiceImpl service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private CompanySite activeSite() {
        return new CompanySite(siteId, companyId, "HQ", null, true, null, null);
    }

    private List<B2bMembership> claim(CompanyRole role) {
        return List.of(new B2bMembership(companyId, role, List.of()));
    }

    @Test
    void deactivate_siteWithActiveOrders_throwsSiteInUse() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), companyId, actorId,
                        CompanyRole.ADMIN, null, null)));
        when(companySiteRepository.findByIdForUpdate(siteId)).thenReturn(activeSite());
        when(orderRepository.countActiveOrdersForSite(siteId)).thenReturn(3L);

        assertThatThrownBy(() -> service.update(companyId, siteId, actorId, claim(CompanyRole.ADMIN),
                null, null, false))
                .isInstanceOf(SiteInUseException.class);
        verify(companySiteRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivate_siteWithNoActiveOrders_succeeds() {
        when(companyMemberRepository.findByCompanyIdAndUserId(companyId, actorId))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), companyId, actorId,
                        CompanyRole.ADMIN, null, null)));
        when(companySiteRepository.findByIdForUpdate(siteId)).thenReturn(activeSite());
        when(orderRepository.countActiveOrdersForSite(siteId)).thenReturn(0L);
        when(companySiteRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        CompanySite updated = service.update(companyId, siteId, actorId, claim(CompanyRole.ADMIN),
                null, null, false);

        assertThat(updated.active()).isFalse();
    }

    @Test
    void update_crossCompanySiteId_returns404() {
        UUID otherCompany = UUID.randomUUID();
        when(companyMemberRepository.findByCompanyIdAndUserId(otherCompany, actorId))
                .thenReturn(Optional.of(new CompanyMember(UUID.randomUUID(), otherCompany, actorId,
                        CompanyRole.OWNER, null, null)));
        when(companySiteRepository.findByIdForUpdate(siteId))
                .thenReturn(new CompanySite(siteId, companyId, "HQ", null, true, null, null));

        assertThatThrownBy(() -> service.update(otherCompany, siteId, actorId,
                List.of(new B2bMembership(otherCompany, CompanyRole.OWNER, List.of())),
                null, null, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void list_nonMember_gets404() {
        assertThatThrownBy(() -> service.listSites(companyId, List.of()))
                .isInstanceOf(NotFoundException.class);
    }
}

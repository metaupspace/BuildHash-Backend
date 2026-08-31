package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SiteInUseException;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.domain.port.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySiteServiceImplTest {

    @Mock
    private B2bAuthorizer authorizer;

    @Mock
    private CompanySiteRepository companySiteRepository;

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

    @Test
    void create_authorizesSiteManage_critical() {
        when(companySiteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(companyId, actorId, "Depot", null);

        verify(authorizer).authorize(actorId, companyId, CompanyPermission.SITE_MANAGE, null, true);
    }

    @Test
    void deactivate_siteWithActiveOrders_throwsSiteInUse() {
        when(companySiteRepository.findByIdForUpdate(siteId)).thenReturn(activeSite());
        when(orderRepository.countActiveOrdersForSite(siteId)).thenReturn(3L);

        assertThatThrownBy(() -> service.update(companyId, siteId, actorId, null, null, false))
                .isInstanceOf(SiteInUseException.class);
        verify(companySiteRepository, never()).save(any());
    }

    @Test
    void deactivate_siteWithNoActiveOrders_succeeds_andSiteScopeChecked() {
        when(companySiteRepository.findByIdForUpdate(siteId)).thenReturn(activeSite());
        when(orderRepository.countActiveOrdersForSite(siteId)).thenReturn(0L);
        when(companySiteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanySite updated = service.update(companyId, siteId, actorId, null, null, false);

        // site mutation carries the resource site — the authorizer enforces site scope too
        verify(authorizer).authorize(actorId, companyId, CompanyPermission.SITE_MANAGE, siteId, true);
        assertThat(updated.active()).isFalse();
    }

    @Test
    void update_crossCompanySiteId_returns404() {
        UUID otherCompany = UUID.randomUUID();
        when(companySiteRepository.findByIdForUpdate(siteId))
                .thenReturn(new CompanySite(siteId, companyId, "HQ", null, true, null, null));

        assertThatThrownBy(() -> service.update(otherCompany, siteId, actorId, null, null, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void list_authorizesSiteView_read() {
        service.listSites(companyId, actorId);
        verify(authorizer).authorize(actorId, companyId, CompanyPermission.SITE_VIEW, null, false);
    }
}

package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.SearchIndexAdmin;
import com.builddash.backend.domain.service.ProductSyncProjectionBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogReindexerTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ProductSyncProjectionBuilder projectionBuilder = mock(ProductSyncProjectionBuilder.class);
    private final SearchIndexAdmin searchIndexAdmin = mock(SearchIndexAdmin.class);
    private final CatalogOutboxEventRepository outboxRepository = mock(CatalogOutboxEventRepository.class);

    private final CatalogReindexer reindexer = new CatalogReindexer(
            productRepository, categoryRepository, projectionBuilder, searchIndexAdmin, outboxRepository);

    @Test
    void reindex_deletesSupersededIndexAfterSwap() {
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findPage(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(searchIndexAdmin.createIndex()).thenReturn("products_v_new");
        when(searchIndexAdmin.resolveAlias("products")).thenReturn("products_v_old");

        reindexer.reindex();

        // The leak that filled the cluster's 1000-shard cap: the superseded blue-green
        // index must be deleted once the alias points at the new one
        InOrder inOrder = inOrder(searchIndexAdmin);
        inOrder.verify(searchIndexAdmin).swapAlias("products", "products_v_new");
        inOrder.verify(searchIndexAdmin).deleteIndex("products_v_old");
    }

    @Test
    void reindex_firstRunWithNoAlias_deletesNothing() {
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findPage(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(searchIndexAdmin.createIndex()).thenReturn("products_v_first");
        when(searchIndexAdmin.resolveAlias("products")).thenReturn(null);

        reindexer.reindex();

        verify(searchIndexAdmin, never()).deleteIndex(anyString());
    }

    @Test
    void reindex_deleteFailure_doesNotBreakOutboxReconcileAfterSwap() {
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(productRepository.findPage(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(searchIndexAdmin.createIndex()).thenReturn("products_v_new");
        when(searchIndexAdmin.resolveAlias("products")).thenReturn("products_v_old");
        when(outboxRepository.findByStatus(any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("delete failed"))
                .when(searchIndexAdmin).deleteIndex(anyString());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> reindexer.reindex());

        // reconcileOutbox sweeps both statuses (PENDING + PUBLISHED)
        verify(outboxRepository, org.mockito.Mockito.times(2)).findByStatus(any());
        verify(outboxRepository, never()).markProcessed(any());
    }
}

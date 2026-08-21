package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.StaleOrderSweepService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaleOrderSweepJob {

    private final StaleOrderSweepService staleOrderSweepService;

    @Scheduled(fixedDelay = 60000)
    public void run() {
        staleOrderSweepService.sweepStaleOrders();
    }
}

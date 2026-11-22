package com.bzd.balance.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RecognitionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionRecoveryService.class);

    private final VideoMapper videoMapper;

    public RecognitionRecoveryService(VideoMapper videoMapper) {
        this.videoMapper = videoMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failOrphanedAnalysisJobs() {
        int count = videoMapper.failInterruptedAnalyses();
        if (count > 0) {
            log.warn("Marked {} interrupted ANALYZING videos as FAILED", count);
        }
    }
}

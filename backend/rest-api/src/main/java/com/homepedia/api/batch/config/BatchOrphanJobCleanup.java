package com.homepedia.api.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Marks any {@code STARTED} Spring Batch job execution as {@code ABANDONED}
 * at application startup. After a hard pod kill (OOM, network blip, deploy)
 * the JVM cannot mark its own jobs as failed — they remain forever STARTED
 * in {@code batch_job_execution} and prevent the same job from being re-run
 * (Spring Batch refuses to launch a new instance while another is "running").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchOrphanJobCleanup {

	private final JobExplorer jobExplorer;
	private final JobOperator jobOperator;

	@EventListener(ApplicationReadyEvent.class)
	public void abandonOrphanedJobs() {
		for (String jobName : jobExplorer.getJobNames()) {
			for (var exec : jobExplorer.findRunningJobExecutions(jobName)) {
				log.warn("Abandoning orphan job execution {} ({}) left STARTED across restart", exec.getId(), jobName);
				try {
					jobOperator.abandon(exec.getId());
				} catch (Exception e) {
					log.error("Failed to abandon orphan job execution {}: {}", exec.getId(), e.getMessage(), e);
				}
			}
		}
	}
}

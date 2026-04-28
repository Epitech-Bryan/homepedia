package com.homepedia.api.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Marks any Spring Batch JobExecution / StepExecution still in {@code STARTED}
 * / {@code STARTING} / {@code STOPPING} as {@code FAILED} on application start.
 * Without this, a pod killed mid-import leaves the row in {@code STARTED}
 * forever — the admin console then shows the job as "en cours…" indefinitely
 * and refuses to relaunch it.
 *
 * <p>
 * We don't need the full {@code Job} bean to fix the row: a direct UPDATE
 * against the Spring Batch metadata tables is the documented recovery path for
 * crash-killed executions. Run before any
 * {@code @Scheduled}/{@code BatchScheduler} code so the next scheduled run sees
 * a clean state.
 */
@Slf4j
@Configuration
public class StaleBatchExecutionsCleaner {

	@Bean
	public ApplicationRunner cleanStaleBatchExecutionsAtBoot(JdbcTemplate jdbc) {
		return args -> {
			final int steps = jdbc.update("""
					UPDATE batch_step_execution
					   SET status = 'FAILED',
					       end_time = COALESCE(end_time, NOW()),
					       exit_code = 'FAILED',
					       exit_message = 'Marked FAILED on app start: pod was terminated during execution'
					 WHERE status IN ('STARTED','STARTING','STOPPING')
					""");
			final int jobs = jdbc.update("""
					UPDATE batch_job_execution
					   SET status = 'FAILED',
					       end_time = COALESCE(end_time, NOW()),
					       exit_code = 'FAILED',
					       exit_message = 'Marked FAILED on app start: pod was terminated during execution'
					 WHERE status IN ('STARTED','STARTING','STOPPING')
					""");
			if (jobs > 0 || steps > 0) {
				log.warn("Cleaned up {} orphan job executions and {} orphan step executions on boot", jobs, steps);
			} else {
				log.debug("No orphan Spring Batch executions to clean up on boot");
			}
		};
	}
}

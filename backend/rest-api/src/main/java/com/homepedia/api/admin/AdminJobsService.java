package com.homepedia.api.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdminJobsService {

	/**
	 * Whitelist of job names exposed to the admin console, in display order. Each
	 * entry maps the API key (URL slug) to the Spring Batch Job bean name.
	 */
	public static final Map<String, String> JOB_NAMES;
	static {
		final var map = new LinkedHashMap<String, String>();
		map.put("dvf", "dvfImportJob");
		map.put("insee", "inseeImportJob");
		map.put("geo", "geoJsonImportJob");
		map.put("dpe", "dpeImportJob");
		map.put("health", "healthImportJob");
		map.put("reviews", "reviewImportJob");
		map.put("economy", "economyImportJob");
		map.put("education", "educationImportJob");
		map.put("environment", "environmentImportJob");
		map.put("infrastructure", "infrastructureImportJob");
		JOB_NAMES = Map.copyOf(map);
	}

	private final JobLauncher jobLauncher;
	private final JobExplorer jobExplorer;
	private final TaskExecutor adminTaskExecutor;
	private final Map<String, Job> jobsByName;

	public AdminJobsService(JobLauncher jobLauncher, JobExplorer jobExplorer, TaskExecutor adminTaskExecutor,
			Map<String, Job> jobsByName) {
		this.jobLauncher = jobLauncher;
		this.jobExplorer = jobExplorer;
		this.adminTaskExecutor = adminTaskExecutor;
		this.jobsByName = jobsByName;
	}

	public Map<String, JobStatusView> statusAll() {
		final var out = new LinkedHashMap<String, JobStatusView>();
		for (var entry : JOB_NAMES.entrySet()) {
			final var slug = entry.getKey();
			final var beanName = entry.getValue();
			out.put(slug, statusOf(beanName));
		}
		return out;
	}

	public void trigger(String slug) {
		trigger(slug, Map.of());
	}

	/**
	 * Async-launch a Spring Batch job by slug, optionally forwarding extra job
	 * parameters. Numeric strings (e.g. {@code year=2024}) are passed as Longs so
	 * jobs can read them via {@code @Value("#{jobParameters['year']}") Long};
	 * everything else is forwarded as a String.
	 */
	public void trigger(String slug, Map<String, String> extraParams) {
		final var beanName = JOB_NAMES.get(slug);
		if (beanName == null) {
			throw new UnknownJobException(slug);
		}
		final var job = jobsByName.get(beanName);
		if (job == null) {
			throw new UnknownJobException(slug);
		}
		if (!jobExplorer.findRunningJobExecutions(beanName).isEmpty()) {
			throw new JobAlreadyRunningException(slug);
		}
		final var builder = new JobParametersBuilder().addLong("timestamp", System.currentTimeMillis());
		if (extraParams != null) {
			for (var entry : extraParams.entrySet()) {
				addTypedParam(builder, entry.getKey(), entry.getValue());
			}
		}
		final var params = builder.toJobParameters();
		adminTaskExecutor.execute(() -> {
			try {
				log.info("Manual job trigger: {} (params={})", beanName, extraParams);
				jobLauncher.run(job, params);
			} catch (Exception e) {
				log.error("Manual job '{}' failed: {}", beanName, e.getMessage(), e);
			}
		});
	}

	private static void addTypedParam(JobParametersBuilder builder, String key, String value) {
		if (key == null || value == null) {
			return;
		}
		try {
			builder.addLong(key, Long.parseLong(value));
		} catch (NumberFormatException e) {
			builder.addString(key, value);
		}
	}

	private JobStatusView statusOf(String beanName) {
		final boolean running = !jobExplorer.findRunningJobExecutions(beanName).isEmpty();
		Instant lastRunAt = null;
		String lastStatus = null;
		Long lastDurationMs = null;
		final List<JobInstance> instances = jobExplorer.findJobInstancesByJobName(beanName, 0, 1);
		if (!instances.isEmpty()) {
			final var execs = jobExplorer.getJobExecutions(instances.get(0));
			if (!execs.isEmpty()) {
				final var last = execs.get(0);
				lastRunAt = toInstant(last.getStartTime());
				lastStatus = last.getStatus().toString();
				if (last.getStartTime() != null && last.getEndTime() != null) {
					lastDurationMs = Duration.between(last.getStartTime(), last.getEndTime()).toMillis();
				}
			}
		}
		final BatchStatus state = running ? BatchStatus.STARTED : BatchStatus.UNKNOWN;
		return new JobStatusView(running ? "RUNNING" : "IDLE", lastRunAt, lastStatus, state.toString(),
				lastDurationMs);
	}

	private static Instant toInstant(LocalDateTime ldt) {
		return ldt == null ? null : ldt.atZone(ZoneId.systemDefault()).toInstant();
	}

	public record JobStatusView(String state, Instant lastRunAt, String lastStatus, String lastBatchStatus,
			Long lastDurationMs) {
	}
}

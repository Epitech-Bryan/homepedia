package com.homepedia.api.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class JobAlreadyRunningException extends RuntimeException {

	public JobAlreadyRunningException(String jobName) {
		super("Job '" + jobName + "' is already running");
	}
}

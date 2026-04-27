package com.homepedia.api.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UnknownJobException extends RuntimeException {

	public UnknownJobException(String jobName) {
		super("Unknown job: " + jobName);
	}
}

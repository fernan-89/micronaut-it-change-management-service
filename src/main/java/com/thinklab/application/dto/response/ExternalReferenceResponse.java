package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ExternalReferenceResponse(String system, String externalId) {}

package com.crowded.notifications.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the {@code @Scheduled} tasks, such as the heartbeat of the live streams. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {}

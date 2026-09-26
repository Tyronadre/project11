package de.tyro.project11.attendance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttendanceMailSettings.class)
@EnableScheduling
public class AttendanceMailConfig {}

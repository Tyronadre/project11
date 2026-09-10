package de.tyro.project11.calendar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CalendarConfig {
    @Bean
    Clock calendarClock() {
        return Clock.system(CalendarTime.BERLIN);
    }
}

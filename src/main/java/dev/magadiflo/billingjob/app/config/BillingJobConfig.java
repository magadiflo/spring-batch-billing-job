package dev.magadiflo.billingjob.app.config;

import dev.magadiflo.billingjob.app.jobs.BillingJob;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Traducción: Billing = Facturación
 * Esta clase será un marcador de posición para los beans relacionados con Spring Batch (Jobs, Steps, etc)
 */
@Configuration
public class BillingJobConfig {

    @Bean
    public Job job(JobRepository jobRepository) {
        return new BillingJob(jobRepository);
    }

}

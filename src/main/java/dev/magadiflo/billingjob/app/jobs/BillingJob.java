package dev.magadiflo.billingjob.app.jobs;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.repository.JobRepository;

public class BillingJob implements Job {

    private final JobRepository jobRepository;

    public BillingJob(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public String getName() {
        return "BillingJob";
    }

    @Override
    public void execute(JobExecution execution) {
        System.out.println("Procesando información de facturación (billing)");

        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        this.jobRepository.update(execution);
    }
}

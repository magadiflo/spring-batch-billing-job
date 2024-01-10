package dev.magadiflo.billingjob.app.jobs;

import org.springframework.batch.core.*;
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
        JobParameters jobParameters = execution.getJobParameters();
        String inputFile = jobParameters.getString("input.file");

        System.out.println("Procesando información de facturación desde el archivo " + inputFile);

        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        this.jobRepository.update(execution);
    }
}

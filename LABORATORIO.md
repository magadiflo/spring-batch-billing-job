# LABORATORIO

---

## ¿Qué vamos a construir?

Creará una aplicación por lotes que genera informes de facturación para una empresa de telefonía móvil imaginaria
llamada Spring Cellular. La aplicación almacena la información de facturación en una base de datos relacional y genera
un informe de facturación. Esta aplicación se basa en Spring Boot y utiliza las características de Spring Batch para
crear un sistema de procesamiento por lotes robusto que se puede reiniciar y es tolerante a fallos.

Implementará un `Job` por lotes denominado `BillingJob` que está diseñado de la siguiente manera:

![billing job](./assets/01.intro-lesson-billing-job.svg)

El trabajo de facturación se estructura en los siguientes pasos:

- **Paso de preparación del archivo:** copia el archivo que contiene el consumo mensual de los clientes de Spring
  Cellular de un servidor de archivos a un área de preparación.

- **Paso de ingestión del archivo:** ingiere el archivo en una tabla de base de datos relacional que contiene los datos
  utilizados para generar el informe de facturación.

- **Paso de generación del informe:** procesa la información de facturación de la tabla de la base de datos y genera un
  archivo plano que contiene los datos de los clientes que han gastado más de 150,00 USD.

## Dependencias

Para la creación de este proyecto se utilizó la dependencia de `Spring Batch` y el Driver de `PostgreSQL`:

````xml
<!--Spring Boot 3.2.1-->
<!--Java 21-->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.batch</groupId>
        <artifactId>spring-batch-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
````

## Preparar el proyecto para Spring Batch

Creamos una clase de configuración para nuestro batch de **facturación (Billing)**, donde más adelante definiremos los
beans relacionados con Spring Batch (Jobs, Steps, etc):

````java

@Configuration
public class BillingJobConfig {
}
````

## Configurando base de datos

### 1. Preparar la base de datos con las tablas de metadatos de Spring Batch

En esta sección, creará las tablas de metadatos de Spring Batch en la base de datos. Esta es la única vez que tendrá que
hacerlo a efectos de aprendizaje.

Primero, crearemos una base de datos contenerizada de Postgres, para eso en la raíz del proyecto creamos el
archivo `compose.yml` y agregamos la siguiente configuración:

````yml
services:
  postgres:
    container_name: postgres
    image: postgres:15.2-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: db_spring_batch
      POSTGRES_USER: magadiflo
      POSTGRES_PASSWORD: magadiflo
    ports:
      - 5433:5432
    expose:
      - 5433
````

Ahora, ejecutamos el comando de docker compose para levantar el contenedor de nuestra base de datos y a continuación
verificamos que esté ejecutándose:

````bash
M:\PROGRAMACION\DESARROLLO_JAVA_SPRING\11.spring_academy\spring-batch-billing-job (main -> origin
$ docker compose up -d
[+] Building 0.0s (0/0)
[+] Running 2/2
✔ Network spring-batch-billing-job_default  Created
✔ Container postgres                        Started

$ docker container ls -a
CONTAINER ID   IMAGE                  COMMAND                  CREATED         STATUS         PORTS                              NAMES
a681802663c0   postgres:15.2-alpine   "docker-entrypoint.s…"   3 minutes ago   Up 3 minutes   5433/tcp, 0.0.0.0:5433->5432/tcp   postgres
````

Utilizando `DBeaver` nos conectamos a la base de datos de postgres que está dentro del contenedor y ejecutamos el
siguiente script:

````sql
CREATE TABLE BATCH_JOB_INSTANCE  (
    JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT ,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ;
CREATE TABLE BATCH_JOB_EXECUTION  (
    JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT  ,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP DEFAULT NULL ,
    END_TIME TIMESTAMP DEFAULT NULL ,
    STATUS VARCHAR(10) ,
    EXIT_CODE VARCHAR(2500) ,
    EXIT_MESSAGE VARCHAR(2500) ,
    LAST_UPDATED TIMESTAMP,
    constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
    references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ;
CREATE TABLE BATCH_JOB_EXECUTION_PARAMS  (
    JOB_EXECUTION_ID BIGINT NOT NULL ,
    PARAMETER_NAME VARCHAR(100) NOT NULL ,
    PARAMETER_TYPE VARCHAR(100) NOT NULL ,
    PARAMETER_VALUE VARCHAR(2500) ,
    IDENTIFYING CHAR(1) NOT NULL ,
    constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
    references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;
CREATE TABLE BATCH_STEP_EXECUTION  (
    STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME TIMESTAMP NOT NULL,
    START_TIME TIMESTAMP DEFAULT NULL ,
    END_TIME TIMESTAMP DEFAULT NULL ,
    STATUS VARCHAR(10) ,
    COMMIT_COUNT BIGINT ,
    READ_COUNT BIGINT ,
    FILTER_COUNT BIGINT ,
    WRITE_COUNT BIGINT ,
    READ_SKIP_COUNT BIGINT ,
    WRITE_SKIP_COUNT BIGINT ,
    PROCESS_SKIP_COUNT BIGINT ,
    ROLLBACK_COUNT BIGINT ,
    EXIT_CODE VARCHAR(2500) ,
    EXIT_MESSAGE VARCHAR(2500) ,
    LAST_UPDATED TIMESTAMP,
    constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
    references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;
CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT  (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT ,
    constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
    references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ;
CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT  (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT ,
    constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
    references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ;
CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
````

### 2. Configurando las propiedades de la base de datos en Spring Boot

Agregaremos las siguientes propiedades en nuestro `application.yml`:

````yaml
server:
  port: 8080

spring:
  application:
    name: spring-batch-billing-job

  datasource:
    url: jdbc:postgresql://localhost:5433/db_spring_batch
    username: magadiflo
    password: magadiflo
````

---

# MODULE 1: Create, run and test your Job

---

## Implementa tu primer Job

En la lección anterior, explicamos que un `Job` por lotes es una entidad que encapsula todo un proceso por lotes. En
este Laboratorio, aprenderá a implementar, ejecutar y probar un `Job` por lotes en una aplicación basada en Spring
Batch y Spring Boot.

Crearemos una implementación de `Job` que imprima información de **facturación (billing)** de procesamiento en la
consola. Casi nunca tendrás que implementar tú mismo la interfaz Job, ya que **Spring Batch proporciona unas cuantas
implementaciones listas para usar.**

En una futura lección, crearemos todos los pasos que definen nuestro `Job` utilizando una de estas implementaciones.
Por ahora, para entender las responsabilidades de un `Job` de Spring Batch, implementaremos la interfaz `Job` como
sigue:

````java
public class BillingJob implements Job {

    // Le damos un nombre a nuestro Job dentro del método getName()
    @Override
    public String getName() {
        return "BillingJob";
    }

    // Implementamos el método execute() donde imprimimos el mensaje en consola
    @Override
    public void execute(JobExecution execution) {
        System.out.println("Procesando información de facturación (billing)");
    }
}
````

Habiendo implementado nuestro job pasamos a configurarlo dentro de la clase de configuración que creamos en capítulos
iniciales:

````java
/**
 * Traducción: Billing = Facturación
 * Esta clase será un marcador de posición para los beans relacionados con Spring Batch (Jobs, Steps, etc)
 */
@Configuration
public class BillingJobConfig {

    @Bean
    public Job job() {
        return new BillingJob();
    }

}
````

Ya estamos listos para ejecutar nuestro `Job`, así que vamos a ejecutarlo y a ver qué pasa.

Una de las características de Spring Boot a la hora de soportar Spring Batch es la ejecución automática de cualquier
`Job` bean definido en el contexto de la aplicación al inicio de la misma. Así, **para lanzar el `Job`, basta con
iniciar la aplicación Spring Boot.**

Spring Boot busca nuestro `Job` en el contexto de la aplicación y lo ejecuta utilizando el `JobLauncher`, **que está
autoconfigurado y listo para que lo utilicemos.**

Deberías ver algo como la siguiente salida en la pestaña TERMINAL del Editor:

````bash
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.1)

2024-01-05T10:07:33.723-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] d.m.b.a.SpringBatchBillingJobApplication : Starting SpringBatchBillingJobApplication using Java 21.0.1 with PID 17316 (M:\PROGRAMACION\DESARROLLO_JAVA_SPRING\11.spring_academy\spring-batch-billing-job\target\classes started by USUARIO in M:\PROGRAMACION\DESARROLLO_JAVA_SPRING\11.spring_academy\spring-batch-billing-job)
2024-01-05T10:07:33.729-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] d.m.b.a.SpringBatchBillingJobApplication : No active profile set, falling back to 1 default profile: "default"
2024-01-05T10:07:35.225-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.boot.autoconfigure.jdbc.DataSourceConfiguration$Hikari' of type [org.springframework.boot.autoconfigure.jdbc.DataSourceConfiguration$Hikari] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.362-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'spring.datasource-org.springframework.boot.autoconfigure.jdbc.DataSourceProperties' of type [org.springframework.boot.autoconfigure.jdbc.DataSourceProperties] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.364-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration$PooledDataSourceConfiguration' of type [org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration$PooledDataSourceConfiguration] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.370-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'jdbcConnectionDetails' of type [org.springframework.boot.autoconfigure.jdbc.PropertiesJdbcConnectionDetails] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.416-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'dataSource' of type [com.zaxxer.hikari.HikariDataSource] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.422-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration$JdbcTransactionManagerConfiguration' of type [org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration$JdbcTransactionManagerConfiguration] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.441-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizationAutoConfiguration' of type [org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizationAutoConfiguration] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.460-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'transactionExecutionListeners' of type [org.springframework.boot.autoconfigure.transaction.ExecutionListenersTransactionManagerCustomizer] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.472-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'spring.transaction-org.springframework.boot.autoconfigure.transaction.TransactionProperties' of type [org.springframework.boot.autoconfigure.transaction.TransactionProperties] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.474-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'platformTransactionManagerCustomizers' of type [org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.493-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'transactionManager' of type [org.springframework.jdbc.support.JdbcTransactionManager] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.499-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'spring.batch-org.springframework.boot.autoconfigure.batch.BatchProperties' of type [org.springframework.boot.autoconfigure.batch.BatchProperties] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). Is this bean getting eagerly injected into a currently created BeanPostProcessor [jobRegistryBeanPostProcessor]? Check the corresponding BeanPostProcessor declaration and its dependencies.
2024-01-05T10:07:35.523-05:00  WARN 17316 --- [spring-batch-billing-job] [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration$SpringBootBatchConfiguration' of type [org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration$SpringBootBatchConfiguration] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying). The currently created BeanPostProcessor [jobRegistryBeanPostProcessor] is declared through a non-static factory method on that class; consider declaring it as static instead.
2024-01-05T10:07:35.610-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2024-01-05T10:07:36.204-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@173f73e7
2024-01-05T10:07:36.207-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2024-01-05T10:07:36.763-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] d.m.b.a.SpringBatchBillingJobApplication : Started SpringBatchBillingJobApplication in 4.089 seconds (process running for 4.972)
2024-01-05T10:07:36.773-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] o.s.b.a.b.JobLauncherApplicationRunner   : Running default command line with: []
2024-01-05T10:07:36.945-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] o.s.b.c.l.support.SimpleJobLauncher      : Job: [dev.magadiflo.billingjob.app.jobs.BillingJob@52e04737] launched with the following parameters: [{}]
Procesando información de facturación (billing)
2024-01-05T10:07:36.951-05:00  INFO 17316 --- [spring-batch-billing-job] [           main] o.s.b.c.l.support.SimpleJobLauncher      : Job: [dev.magadiflo.billingjob.app.jobs.BillingJob@52e04737] completed with the following parameters: [{}] and the following status: [STARTING]
2024-01-05T10:07:36.961-05:00  INFO 17316 --- [spring-batch-billing-job] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown initiated...
2024-01-05T10:07:36.988-05:00  INFO 17316 --- [spring-batch-billing-job] [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.
````

El `Job` se ha ejecutado correctamente y se ha completado con éxito, ya que vemos el mensaje de información de
facturación de procesamiento en la salida estándar, tal y como se esperaba.

### Verificando el Job

1. **Comprobemos el estado del `Job` en la base de datos.**

   Para eso podemos utilizar `DBeaver` o como en esta oportunidad, utilizaré la línea de comando para ingresar al
   contendor y dentro de él abrir la línea de comando psql de postgres:

    ````bash
    $ docker container ls -a
    CONTAINER ID   IMAGE                  COMMAND                  CREATED        STATUS          PORTS                              NAMES
    38479af4786a   postgres:15.2-alpine   "docker-entrypoint.s…"   16 hours ago   Up 33 minutes   5433/tcp, 0.0.0.0:5433->5432/tcp   postgres                              NAMES
    
    $ docker exec -it postgres /bin/sh
    / # psql -U magadiflo -d db_spring_batch
    psql (15.2)
    Type "help" for help.
    
    db_spring_batch=# \d
                          List of relations
     Schema |             Name             |   Type   |   Owner
    --------+------------------------------+----------+-----------
     public | batch_job_execution          | table    | magadiflo
     public | batch_job_execution_context  | table    | magadiflo
     public | batch_job_execution_params   | table    | magadiflo
     public | batch_job_execution_seq      | sequence | magadiflo
     public | batch_job_instance           | table    | magadiflo
     public | batch_job_seq                | sequence | magadiflo
     public | batch_step_execution         | table    | magadiflo
     public | batch_step_execution_context | table    | magadiflo
     public | batch_step_execution_seq     | sequence | magadiflo
    (9 rows)
    
    db_spring_batch=# SELECT * FROM batch_job_execution;
     job_execution_id | version | job_instance_id |        create_time         | start_time | end_time |  status  | exit_code | exit_message |        last_updated
    ------------------+---------+-----------------+----------------------------+------------+----------+----------+-----------+--------------+----------------------------
                    1 |       0 |               1 | 2024-01-05 10:07:36.899313 |            |          | STARTING | UNKNOWN   |              | 2024-01-05 10:07:36.900391
    (1 row)
    ````

   Spring Batch ha registrado correctamente la ejecución del `Job` en la base de datos, pero el estado del `Job` es
   `STARTING` y su código de salida es `UNKNOWN`. **¿Cómo es posible si nuestro Job se ha ejecutado y completado
   correctamente?**

   Aquí es donde entra en juego la responsabilidad del `Job` de reportar su `status` y `exit_code` al `JobRepository`.
   Arreglémoslo.


2. **Establezcamos los estados y utilicemos el `JobRepository`.**

   En este paso, primero pasamos una referencia `JobRepository` como parámetro constructor a nuestro BillingJob.
   Usaremos el `JobRepository` para guardar información importante sobre nuestro `Job`.

   A continuación, hemos actualizado el método `execute` para establecer el estado de ejecución del `Job`, así como su
   estado de salida.

   Finalmente, emitimos un `jobRepository.update(execution)` para actualizar la ejecución del `Job` en la base de datos.

    ````java
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
    ````

3. **Entendiendo los `updates`.**

   Lo que hay que entender aquí es que es responsabilidad de la implementación del `Job` informar de su estado al
   `JobRepository`.

    - El estado del `Job` por lotes **indica el estado de la ejecución.**
      Por ejemplo, si el `Job` se está ejecutando el estado del lote es `BatchStatus.STARTED`. Si falla, es
      `BatchStatus.FAILED`, y si finaliza con éxito, es `BatchStatus.COMPLETED`.

    - Como nuestro `Job` se ha completado con éxito estableceremos el estado de ejecución a `BatchStatus.COMPLETED` y el
      estado de salida también a un `ExitStatus.COMPLETED`.

   Ahora que hemos actualizado la implementación de nuestro `Job` para que utilice un `JobRepository` para informar de
   su
   estado, necesitamos añadir una referencia al `JobRepository` en la definición de nuestro `bean Job`.


4. **Suministrando el `JobRepository` al `Job`.**

   Gracias a Spring Boot, se ha autoconfigurado un `JobRepository` con el datasource configurado para nuestra base de
   datos PostgreSQL.

   Este `JobRepository` está listo para que lo utilicemos a través de una inyección de dependencia, en nuestro caso
   utilizamos la inyección de dependencía vía parámetro del método en nuestro Job bean.

   ````java
   @Configuration
   public class BillingJobConfig {   
       @Bean
       public Job job(JobRepository jobRepository) {
           return new BillingJob(jobRepository);
       }   
   }
   ````
5. **Limpiando y volviendo a ejecutar el `Job`.**

   Ahora vamos a intentar volver a ejecutar el `Job` y comprobar su estado en la base de datos.

   Pero antes de volver a ejecutar el `Job`, vamos a limpiar la base de datos para eliminar el ruido de la ejecución
   anterior. En la pestaña Terminal, ejecute los siguientes comandos:

   ````bash
   $ docker compose down
   $ docker compose up -d
   ````

   Luego podemos ingresar a la base de datos dentro del contenedor y usar la línea de comandos de psql de postgres para
   poder ejecutar el script con las tablas y secuencias de la base de datos:

   ````bash
   $ docker exec -it postgres /bin/sh
   / # psql -U magadiflo -d db_spring_batch
   psql (15.2)
   Type "help" for help.
   
   db_spring_batch=# CREATE TABLE BATCH_JOB_INSTANCE  (     JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,     VERSION BIGINT ,     JOB_NAME VARCHAR(100) NOT NULL,     JOB_KEY VARCHAR(32) NOT NULL,     constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY) ) ; CREATE TABLE BATCH_JOB_EXECUTION  (     JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,     VERSION BIGINT  ,     JOB_INSTANCE_ID BIGINT NOT NULL,     CREATE_TIME TIMESTAMP NOT NULL,     START_TIME TIMESTAMP DEFAULT NULL ,
       END_TIME TIMESTAMP DEFAULT NULL ,     STATUS VARCHAR(10) ,     EXIT_CODE VARCHAR(2500) ,     EXIT_MESSAGE VARCHAR(2500) ,     LAST_UPDATED TIMESTAMP,     constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)     references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID) ) ; CREATE TABLE BATCH_JOB_EXECUTION_PARAMS  (     JOB_EXECUTION_ID BIGINT NOT NULL ,     PARAMETER_NAME VARCHAR(100) NOT NULL ,     PARAMETER_TYPE VARCHAR(100) NOT NULL ,     PARAMETER_VALUE VARCHAR(2500) ,
      IDENTIFYING CHAR(1) NOT NULL ,     constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)     references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID) ) ; CREATE TABLE BATCH_STEP_EXECUTION  (     STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,     VERSION BIGINT NOT NULL,     STEP_NAME VARCHAR(100) NOT NULL,     JOB_EXECUTION_ID BIGINT NOT NULL,     CREATE_TIME TIMESTAMP NOT NULL,     START_TIME TIMESTAMP DEFAULT NULL ,     END_TIME TIMESTAMP DEFAULT NULL ,     STATUS VARCHAR(10) ,     COMMIT_COUNT BIGINT ,     READ_COUNT BIGINT ,     FILTER_COUNT BIGINT ,     WRITE_COUNT BIGINT ,     READ_SKIP_COUNT BIGINT ,     WRITE_SKIP_COUNT BIGINT ,     PROCESS_SKIP_COUNT BIGINT ,     ROLLBACK_COUNT BIGINT ,     EXIT_CODE VARCHAR(2500) ,     EXIT_MESSAGE VARCHAR(2500) ,     LAST_UPDATED TIMESTAMP,     constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)     references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID) ) ; CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT  (     STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,     SHORT_CONTEXT VARCHAR(2500) NOT NULL,     SERIALIZED_CONTEXT TEXT ,     constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)     references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID) ) ; CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT  (     JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,     SHORT_CONTEXT VARCHAR(2500) NOT NULL,     SERIALIZED_CONTEXT TEXT ,     constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)     references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID) ) ; CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE; CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE; CREATE SEQUENCE BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE SEQUENCE
   CREATE SEQUENCE
   CREATE SEQUENCE
   db_spring_batch=# \d
                         List of relations
    Schema |             Name             |   Type   |   Owner
   --------+------------------------------+----------+-----------
    public | batch_job_execution          | table    | magadiflo
    public | batch_job_execution_context  | table    | magadiflo
    public | batch_job_execution_params   | table    | magadiflo
    public | batch_job_execution_seq      | sequence | magadiflo
    public | batch_job_instance           | table    | magadiflo
    public | batch_job_seq                | sequence | magadiflo
    public | batch_step_execution         | table    | magadiflo
    public | batch_step_execution_context | table    | magadiflo
    public | batch_step_execution_seq     | sequence | magadiflo
   (9 rows)
   ````

   Ahora vuelva a ejecutar el `Job` como se ha mostrado antes **(ejecutar la aplicación)** y compruebe la base de datos.
   El estado de ejecución así como el estado de salida deberían ser ahora `COMPLETED`.

   ````bash
   db_spring_batch=# SELECT * FROM batch_job_execution;
    job_execution_id | version | job_instance_id |        create_time         | start_time | end_time |  status   | exit_code | exit_message |        last_updated
   ------------------+---------+-----------------+----------------------------+------------+----------+-----------+-----------+--------------+----------------------------
                   1 |       1 |               1 | 2024-01-05 11:06:25.115829 |            |          | COMPLETED | COMPLETED |              | 2024-01-05 11:06:25.154192
   (1 row)
   ````

Si el estado del Trabajo es `COMPLETED`, ¡enhorabuena! Ha creado, configurado y ejecutado correctamente su primer
`job` por lotes de Spring.
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

Podemos utilizar `DBeaver` para conectarnos a la base de datos de postgres dentro del contenedor y ejecutar directamente
el script que contiene las tablas y secuencias para definir nuestra base de datos, pero, en mi caso crearé
un `Script de Shell` donde definiré los comandos a utilizar para poder eliminar y crear las tablas desde cero. Para eso,
en la raíz del proyecto crearé un directorio llamado `/scripts` donde agregaré el archivo
llamado `drop-create-tables-database.sh` y definiré los siguientes comandos:

````bash
docker cp ./src/sql/schema-drop-tables.sql postgres:/tmp/
docker cp ./src/sql/schema-create-tables.sql postgres:/tmp/

docker exec postgres psql -f ./tmp/schema-drop-tables.sql -U magadiflo -d db_spring_batch
docker exec postgres psql -f ./tmp/schema-create-tables.sql -U magadiflo -d db_spring_batch
````

**DONDE**

- `docker cp`, comando de docker que permite copiar archivos desde la pc local hacia dentro del contenedor y viceversa.
- `./src/sql/schema-drop-tables.sql`, archivo que estará en dicha ruta de mi pc local para ser copiada.
- `postgres:/tmp/`, `postgres` es el nombre del contenedor y `/tmp/` el directorio de destino dentro del contenedor
  donde copiaremos el archivo.

- `docker exec postgres`, accedemos dentro del contenedor `postgres`.
- `psql`, nos permite utilizar la línea de comandos de postgres dentro del contenedor.
- `-f ./tmp/schema-drop-tables.sql`, utilizará el archivo ubicado en dicho path. Recordemos que dicho archivo lo
  copiamos al inicio.
- `-U magadiflo`, definimos el usuario de la base de datos que es `magadiflo`.
- `-d db_spring_batch`, definimos la base de datos que usaremos.

Ahora, observamos que en los comandos anteriores estamos usando unos archivos que hasta el momento no hemos definido:
`schema-drop-tables.sql` y `schema-create-tables.sql`, así que para que esto funcione debemos crear dichos archivos
en el directorio `/src/sql/`:

`schema-drop-tables.sql`

````sql
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION_CONTEXT;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_CONTEXT;
DROP TABLE IF EXISTS BATCH_STEP_EXECUTION;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION_PARAMS;
DROP TABLE IF EXISTS BATCH_JOB_EXECUTION;
DROP TABLE IF EXISTS BATCH_JOB_INSTANCE;

DROP SEQUENCE IF EXISTS BATCH_STEP_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_SEQ;
````

`schema-create-tables.sql`

````sql
CREATE TABLE BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT ,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
);

CREATE TABLE BATCH_JOB_EXECUTION (
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
);

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT NOT NULL ,
    PARAMETER_NAME VARCHAR(100) NOT NULL ,
    PARAMETER_TYPE VARCHAR(100) NOT NULL ,
    PARAMETER_VALUE VARCHAR(2500) ,
    IDENTIFYING CHAR(1) NOT NULL ,
    constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
     references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION (
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
);

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT ,
    constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
       references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT ,
    constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
      references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
````

Una vez que tenemos creado el archivo `Script de Shell` y los `Scripts SQL` y además tenemos corriendo el contenedor
de postgres, utilizamos el `GitBash` para ejecutar el archivo `drop-create-tables-database.sh`, así que nos posicionamos
en la raíz del proyecto y ejecutamos:

````bash
# Utilizando GitBash
USUARIO@DESKTOP-EGDL8Q6 MINGW64 /m/PROGRAMACION/DESARROLLO_JAVA_SPRING/11.spring_academy/spring-batch-billing-job (feature/create-run-test-job)

$ ./scripts/drop-create-tables-database.sh
psql:tmp/schema-drop-tables.sql:1: NOTICE:  table "batch_step_execution_context" does not exist, skipping
DROP TABLE
DROP TABLE
psql:tmp/schema-drop-tables.sql:2: NOTICE:  table "batch_job_execution_context" does not exist, skipping
psql:tmp/schema-drop-tables.sql:3: NOTICE:  table "batch_step_execution" does not exist, skipping
DROP TABLE
psql:tmp/schema-drop-tables.sql:4: NOTICE:  table "batch_job_execution_params" does not exist, skipping
DROP TABLE
psql:tmp/schema-drop-tables.sql:5: NOTICE:  table "batch_job_execution" does not exist, skipping
DROP TABLE
DROP TABLE
psql:tmp/schema-drop-tables.sql:6: NOTICE:  table "batch_job_instance" does not exist, skipping
psql:tmp/schema-drop-tables.sql:8: NOTICE:  sequence "batch_step_execution_seq" does not exist, skipping
DROP SEQUENCE
psql:tmp/schema-drop-tables.sql:9: NOTICE:  sequence "batch_job_execution_seq" does not exist, skipping
DROP SEQUENCE
psql:tmp/schema-drop-tables.sql:10: NOTICE:  sequence "batch_job_seq" does not exist, skipping
DROP SEQUENCE
CREATE TABLE
CREATE TABLE
CREATE TABLE
CREATE TABLE
CREATE TABLE
CREATE TABLE
CREATE SEQUENCE
CREATE SEQUENCE
CREATE SEQUENCE
````

La primera vez nos saldrá el mensaje `NOTICE:  table "batch_job_execution_context" does not exist, skipping` para cada
una de las tablas creadas, esto es porque la base de datos dentro del contenedor está vacía.

Listo, ahora utilizando nuestra línea de comandos de `Cmder` ingresamos dentro del contenedor para ver que tenemos
los archivos copiados en el directorio `/tmp`:

````bash
docker exec -it postgres /bin/sh
/ # cd tmp/
/tmp # ls
schema-create-tables.sql  schema-drop-tables.sql
/tmp #
````

Ahora, verificamos las tablas creadas utilizando la línea de comando de postgres:

````bash
/tmp # psql -U magadiflo -d db_spring_batch
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
````

Vemos que efectivamente, las tablas y secuencias fueron creadas correctamente. Ahora, cada vez que quiera eliminar las
tablas y secuencias y volverlas a crear dentro del contenedor, simplemente tendría que ejecutar el archivo
`drop-create-tables-database.sh` utilizando el `Git Bash` de `Git`. ¿Por qué? Porque nos proporciona la línea de
comandos donde podemos ejecutar comandos línux o archivos con extensión `.sh` que es para `linux`.

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
   anterior. En la pestaña Terminal de `Git Bash`, ejecute el archivo `drop-create-tables-database.sh`:

   ````bash
   USUARIO@DESKTOP-EGDL8Q6 MINGW64 /m/PROGRAMACION/DESARROLLO_JAVA_SPRING/11.spring_academy/spring-batch-billing-job (feature/create-run-test-job)
   
   $ ./scripts/drop-create-tables-database.sh
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP SEQUENCE
   DROP SEQUENCE
   DROP SEQUENCE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE SEQUENCE
   CREATE SEQUENCE
   CREATE SEQUENCE
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

Si el estado del `Job` es `COMPLETED`, ¡enhorabuena! Ha creado, configurado y ejecutado correctamente su primer
`job` por lotes de Spring.

### Manejo de errores

Al igual que con la ruta de éxito, es responsabilidad del `Job` manejar las excepciones en tiempo de ejecución y
reportar su fallo al `JobRepository`.

Como ejercicio adicional, intente simular una excepción en el método `execute` y reporte un estado de `FAILED` al
repositorio.

Observe como no se espera que el método `execute` lance excepciones, y es responsabilidad de la implementación del
`Job` manejarlas y añadirlas al objeto `JobExecution` para su posterior inspección:

````java
public class BillingJob implements Job {

    /* other codes */

    @Override
    public void execute(JobExecution execution) {
        try {
            throw new Exception("No se puede procesar la información de facturación");
        } catch (Exception exception) {
            execution.addFailureException(exception);
            execution.setStatus(BatchStatus.COMPLETED);
            execution.setExitStatus(ExitStatus.FAILED.addExitDescription(exception.getMessage()));
        } finally {
            this.jobRepository.update(execution);
        }
    }
}
````

A continuación realicemos las siguientes acciones:

1. Limpiamos la base de datos del contenedor de postgres ejecutando mediante el `GitBash` el `Script de Shell` que
   creamos al inicio:

   ````bash
   USUARIO@DESKTOP-EGDL8Q6 MINGW64 /m/PROGRAMACION/DESARROLLO_JAVA_SPRING/11.spring_academy/spring-batch-billing-job (feature/create-run-test-job)
   $ ./scripts/drop-create-tables-database.sh
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP TABLE
   DROP SEQUENCE
   DROP SEQUENCE
   DROP SEQUENCE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE TABLE
   CREATE SEQUENCE
   CREATE SEQUENCE
   CREATE SEQUENCE
   ````

2. Volvemos a ejecutar el `Job` con la lógica de negocio intencionadamente fallida. Además del estado `COMPLETED`,
   también debería aparecer un `exit_code` de estado `FAILED` en la base de datos, así como el mensaje de error en la
   columna `exist_message`:

````bash
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
 job_execution_id | version | job_instance_id |        create_time         | start_time | end_time |  status   | exit_code |                    exit_message                    |        last_updated
------------------+---------+-----------------+----------------------------+------------+----------+-----------+-----------+----------------------------------------------------+----------------------------
                1 |       1 |               1 | 2024-01-05 17:14:23.748417 |            |          | COMPLETED | FAILED    | No se puede procesar la información de facturación | 2024-01-05 17:14:23.789257
(1 row)
````

Una vez que hemos comprobado este manejo de errores, vamos a asegurarnos de revertir el método `execute` a la
implementación correcta, para dejarlo como lo teníamos inicialmente.

````java
public class BillingJob implements Job {

    /* Other codes */

    @Override
    public void execute(JobExecution execution) {
        System.out.println("Procesando información de facturación (billing)");

        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        this.jobRepository.update(execution);
    }
}
````

## Testeando el Job

Ahora que la implementación de nuestro `job` está completa, podemos escribir una prueba para él. Spring Batch
proporciona varias utilidades de prueba para simplificar la comprobación de los componentes Batch. Las veremos más
adelante en el curso.

En esta lección veremos cómo probar un `job` Spring Batch utilizando `JUnit 5` y las utilidades de prueba proporcionadas
por Spring Boot.

1. **Actualiza el `SpringBatchBillingJobApplicationTests`.**
   En la clase de test que nos crea automáticamente Spring Boot, agregamos el código para probar el job:

   ````java
   @SpringBootTest
   @ExtendWith(OutputCaptureExtension.class)
   class SpringBatchBillingJobApplicationTests {
   
       @Autowired
       private Job job;
   
       @Autowired
       private JobLauncher jobLauncher;
   
       @Test
       void testJobExecution(CapturedOutput output) throws Exception {
           // given
           JobParameters jobParameters = new JobParameters();
   
           // when
           JobExecution jobExecution = this.jobLauncher.run(this.job, jobParameters);
   
           // then
           Assertions.assertTrue(output.getOut().contains("Procesando información de facturación (billing)"));
           Assertions.assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());
       }
   }
   ````

2. **Entendiendo la estructura de la clase de test**
    - En primer lugar, anotamos la clase de prueba con `@SpringBootTest`. Al hacerlo, se habilitan las funciones de
      prueba de Spring Boot, que comprenden la carga del contexto de aplicación de Spring, la preparación del contexto
      de prueba, etc.
      ````java
      @SpringBootTest
      @ExtendWith(OutputCaptureExtension.class)
      class SpringBatchBillingJobApplicationTests {
        /*...*/
      }
      ````
    - A continuación, y con el fin de probar nuestro `job` por lotes que escribe la salida en la consola, utilizamos
      la `OutputCaptureExtension` que proporciona Spring Boot para **capturar cualquier salida que se escriba en la
      salida estándar y en la salida de error.**<br>   
      En nuestro caso, necesitamos esta captura de salida para comprobar si el `job` está imprimiendo correctamente el
      mensaje de información de facturación del proceso en la consola.
      ````java
      @SpringBootTest
      @ExtendWith(OutputCaptureExtension.class)
      class SpringBatchBillingJobApplicationTests {
        /*...*/
      }
      ````
    - Podemos entonces autocablear el `job` bajo prueba así como `JobLauncher` desde el contexto de prueba.
      ````java
      @SpringBootTest
      @ExtendWith(OutputCaptureExtension.class)
      class SpringBatchBillingJobApplicationTests {
         @Autowired
         private Job job;

         @Autowired
         private JobLauncher jobLauncher;
         /* other codes */
      }
      ````

3. **Entendiendo el caso de prueba**

   Ahora que la clase de prueba está configurada, podemos escribir el método de prueba `testJobExecution`, que está
   diseñado para:
    - Iniciar el `Job` por lotes con un conjunto de parámetros.
    - Comprobar que la salida del `Job` contiene el mensaje esperado y que su estado se ha actualizado correctamente.
      ````java
   
      @SpringBootTest
      @ExtendWith(OutputCaptureExtension.class)
      class SpringBatchBillingJobApplicationTests {
   
         /*...*/
   
         @Test
         void testJobExecution(CapturedOutput output) throws Exception {
            // given
            JobParameters jobParameters = new JobParameters();
   
            // when
            JobExecution jobExecution = this.jobLauncher.run(this.job, jobParameters);
   
            // then
            Assertions.assertTrue(output.getOut().contains("Procesando información de facturación (billing)"));
            Assertions.assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());
         }
      }
      ````

Eso es todo lo que necesitamos para probar nuestro `Job` de facturación.

4. **Ejecutando el test**

   La prueba debe pasar, lo que significa que nuestro `job` por lotes está haciendo lo que se supone que debe hacer.

   ![run test](./assets/06.runt-test.png)

## Resumen

¡Enhorabuena! Ha creado con éxito su primer `job` Spring Batch con una aplicación basada en Spring Boot.

En este Laboratorio, el contenido ha sido cuidadosamente diseñado para que **implementes la interfaz Job directamente
con fines de aprendizaje.**

**Casi nunca tendrá que implementar esa interfaz directamente**, ya que Spring Batch proporciona implementaciones listas
para usar como `SimpleJob` para `jobs` secuenciales simples basados en `steps` y `FlowJob` para trabajos que requieren
un complejo flujo de ejecución por pasos.

En el siguiente módulo, utilizarás estas clases y aprenderás a estructurar el flujo de trabajo de tu `job` por lotes
con `steps`.
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
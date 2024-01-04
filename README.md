# [Building a Batch Application with Spring Batch](https://spring.academy/courses/building-a-batch-application-with-spring-batch)

Curso dictado por la misma página de Spring `Spring Academy`

---

# INTRODUCCIÓN

## Introducción al procesamiento por lotes

El procesamiento por lotes es un método para procesar grandes volúmenes de datos simultáneamente, en lugar de
procesarlos individualmente, en tiempo real (en ese caso, podríamos hablar de procesamiento por flujos). Este método se
utiliza mucho en muchos sectores, como las finanzas, la fabricación y las telecomunicaciones. El procesamiento por lotes
suele utilizarse para tareas que requieren el tratamiento de grandes cantidades de datos, como el procesamiento de
nóminas o la facturación, así como para tareas que requieren cálculos o análisis que requieren mucho tiempo. **Las
aplicaciones por lotes son efímeras, lo que significa que una vez finalizadas, terminan.**

Este tipo de procesamiento conlleva una serie de retos, como por ejemplo

- Manejo eficiente de grandes cantidades de datos
- Tolerancia a errores humanos y deficiencias de hardware
- Escalabilidad

Cuando llega el momento de proporcionar una aplicación basada en lotes para procesar grandes cantidades de datos de
forma estructurada, Spring Batch proporciona una solución robusta y eficiente. Entonces, ¿qué es exactamente Spring
Batch? ¿Cómo ayuda a afrontar los retos del procesamiento por lotes?.

## Spring Batch Framework

Spring Batch es un framework ligero y completo, diseñado para permitir el desarrollo de aplicaciones por lotes robustas
que son vitales para las operaciones diarias de los sistemas empresariales.

Proporciona todas las funciones necesarias que son esenciales para procesar grandes volúmenes de datos, incluida la
gestión de transacciones, el estado de procesamiento de los trabajos, las estadísticas y las funciones de tolerancia a
fallos. También proporciona funciones avanzadas de escalabilidad que permiten realizar trabajos por lotes de alto
rendimiento mediante técnicas de procesamiento multihilo y partición de datos. Puede utilizar Spring Batch tanto en
casos de uso sencillos (como cargar un archivo en una base de datos) como en casos de uso complejos y de gran volumen (
como mover datos entre bases de datos, transformarlos, etc.).

Spring Batch se integra perfectamente con otras tecnologías Spring, lo que lo convierte en una excelente opción para
escribir aplicaciones por lotes con Spring.

## Lenguaje de dominio batch

Los conceptos clave del modelo de dominio Spring Batch se representan en el siguiente diagrama:

![02.overview-lesson-domain-model.svg](./assets/02.overview-lesson-domain-model.svg)

Un `Job` es una entidad que encapsula todo un proceso por lotes, que se ejecuta de principio a fin sin interrupción.
Un `Job` tiene uno o más `Steps`. Un `Step` es una unidad de trabajo que puede ser una tarea simple (como copiar un
fichero o crear un archivo), o una tarea orientada a ítems (como exportar registros de una tabla de base de datos
relacional a un fichero), en cuyo caso, tendría un `ItemReader`, un `ItemProcessor` (que es opcional), y
un `ItemWriter`.

Un `Job` necesita ser lanzado con un `JobLauncher`, y puede ser lanzado con un conjunto de `JobParameters`. Los
metadatos de ejecución sobre el `Job` que se está ejecutando se almacenan en un `JobRepository`.

Cubriremos cada uno de estos conceptos clave en detalle a lo largo del curso.

## Modelo de dominio por lote

Spring Batch utiliza un modelo robusto y bien diseñado para el dominio del procesamiento por lotes. Proporciona un rico
conjunto de APIs Java con interfaces y clases que representan todos los conceptos clave del procesamiento por lotes
como `Job, Step, JobLauncher, JobRepository`, y más. Utilizaremos estas API en este curso.

Aunque el modelo de dominio de lotes puede implementarse con cualquier tecnología de persistencia (como una base de
datos relacional, una base de datos no relacional, una base de datos gráfica, etc.), Spring Batch proporciona un modelo
relacional de los conceptos de dominio de lotes con tablas de metadatos que se ajustan estrechamente a las clases e
interfaces de la API de Java.

El siguiente diagrama entidad-relación presenta las principales tablas de metadatos:

![03.overview-lesson-relational-model.svg](./assets/03.overview-lesson-relational-model.svg)

- `Job_Instance`: Esta tabla contiene toda la información relevante para la definición de un `Job`, como el nombre
  del `Job` y su clave de identificación.

- `Job_Execution`: Esta tabla contiene toda la información relevante para la ejecución de un `Job`, como la hora de
  inicio, la hora de finalización y el estado. Cada vez que se ejecuta un `Job`, se inserta una nueva fila en esta
  tabla.

- `Job_Execution_Context`: Esta tabla contiene el contexto de ejecución de un `Job`. Un contexto de ejecución es un
  conjunto de pares clave/valor de información en tiempo de ejecución que suele representar el estado que debe
  recuperarse tras un fallo.

- `Step_Execution`: Esta tabla contiene toda la información relevante para la ejecución de un `step`, como la hora de
  inicio, la hora de finalización, el recuento de lectura de elementos y el recuento de escritura de elementos. Cada vez
  que se ejecuta un `step`, se inserta una nueva fila en esta tabla.

- `Step_Execution_Context`: Esta tabla contiene el contexto de ejecución de un `step`. Es similar a la tabla que
  contiene el contexto de ejecución de un `Job`, pero en su lugar almacena el contexto de ejecución de un `step`.

- `Job_Execution_Params`: Esta tabla contiene los parámetros de ejecución de un `Job`.

## Spring Batch Architecture

Spring Batch está diseñado de forma modular y extensible. En el diagrama siguiente se muestra la arquitectura en capas
que admite la facilidad de uso del framework para los usuarios finales:

![04.overview-lesson-architecture.svg](./assets/04.overview-lesson-architecture.svg)

Esta arquitectura en capas destaca tres componentes principales de alto nivel:

- La capa `Application`: contiene el `job` por lotes y el código personalizado escrito por los desarrolladores de la
  aplicación por lotes.

- La capa `Batch Core`: contiene las clases centrales de tiempo de ejecución proporcionadas por Spring Batch que son
  necesarias para crear y controlar los `jobs` por lotes. Incluye implementaciones para `Job` y `Step`, así como
  servicios comunes como `JobLauncher` y `JobRepository`.

- La capa `Batch Infrastructure`: contiene lectores y escritores de elementos comunes proporcionados por Spring Batch,
  además de servicios base como los mecanismos de repetición y reintento, que utilizan tanto los desarrolladores de
  aplicaciones como el propio marco central.

Como desarrollador de Spring Batch, normalmente utilizará las APIs proporcionadas por Spring Batch en los
módulos `Batch Infrastructure` y `Batch Core` para definir sus `jobs` y `steps` en la capa Application. Spring Batch
proporciona una amplia biblioteca de componentes de lotes que puede utilizar de forma inmediata (como lectores de
elementos, escritores de elementos, particionadores de datos, etc.).
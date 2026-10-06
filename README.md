# Post-contenido — Unidad 7: Patrones Arquitectónicos I (Sistema de Multas de Biblioteca)

**Autor:** Reyes  
**Institución:** Universidad de Santander (UDES)  
**Proyecto:** `multas-biblioteca-api`  
**Tecnologías:** Spring Boot 3.2.3, Java 17, Spring Data JPA, H2 Database, Bean Validation, JUnit 5, Mockito, MockMvc, Maven.

---

## 1. Visión General del Sistema

El sistema `multas-biblioteca-api` es una solución diseñada para la gestión integral de penalizaciones pecuniarias por devolución tardía de material bibliográfico en la biblioteca universitaria. La solución abarca desde el cobro tradicional en ventanilla física hasta la integración con pasarelas de pago digitales intercambiables (PagosUDES y Wompi), implementando una evolución arquitectónica justificada que transita desde un enfoque tradicional en capas hacia una arquitectura desacoplada basada en **Puertos y Adaptadores (Arquitectura Hexagonal)**.

---

## 2. Diagrama de Arquitectura y Estructura del Proyecto

### 2.1 Diagrama Arquitectónico (Opción C: Puertos y Adaptadores)

```
                       +-----------------------------------+
                       |        Clientes HTTP / REST       |
                       +-----------------+-----------------+
                                         |
                                         v [JSON / HTTP]
   +-------------------------------------------------------------------------------+
   | CAPA DE PRESENTACIÓN (controller)                                             |
   |   - MultaController: Endpoints REST (/api/multas)                             |
   |   - GenerarMultaRequest: DTO / Record de entrada con Bean Validation           |
   |   - GlobalExceptionHandler: Mapeo de excepciones a códigos HTTP (404,409,400,402) |
   +-------------------------------------+-----------------------------------------+
                                         |
                                         v Invocación de casos de uso
   +-------------------------------------------------------------------------------+
   | CAPA DE APLICACIÓN (service)                                                  |
   |   - MultaService: Orquestación de casos de uso y demarcación @Transactional    |
   +--------------------+------------------------------------+---------------------+
                        |                                    |
                        v Utiliza Entidades y Reglas         v Invoca abstracción
   +--------------------------------------------+   +-------------------------------+
   | CAPA DE DOMINIO PURO (model / domain)      |   | PUERTO SECUNDARIO (domain.port)|
   |   - Multa (Entidad con cálculo y estado)   |   |   - PasarelaPagoPort (Interfaz)|
   |   - EstadoMulta (Enum: PENDIENTE, PAGADA)  |   |   - ResultadoPago (Record)    |
   |   - Excepciones de Dominio (NotFound,      |   |   - PagoRechazadoException    |
   |     LimitePendientes, MultaYaPagada)       |   |   (¡CERO imports Spring/HTTP!) |
   +--------------------------------------------+   +---------------+---------------+
                        ^                                           ^
                        | Implementa JPA                            | Implementa Puerto (DIP)
   +--------------------+-----------------------+   +---------------+---------------+
   | CAPA DE PERSISTENCIA (repository)          |   | CAPA DE INFRAESTRUCTURA (infra)|
   |   - MultaRepository (Spring Data JPA)      |   |   - PagosUdesAdapter          |
   |   - Consultas SQL delegadas:               |   |   - WompiAdapter              |
   |     countByEstudianteIdAndEstado           |   |   - RestTemplateConfig        |
   +--------------------------------------------+   |   (@ConditionalOnProperty)    |
                                                    +-------------------------------+
                                                                    |
                                                                    v [Llamadas REST Externas]
                                                    +-------------------------------+
                                                    | Pasarelas: PagosUDES / Wompi  |
                                                    +-------------------------------+
```

---

### 2.2 Estructura de Paquetes Final

```
multas-biblioteca-api/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/multas/
    │   │   ├── MultasApplication.java               # Punto de entrada @SpringBootApplication
    │   │   │
    │   │   ├── controller/                          # Capa de Presentación REST
    │   │   │   ├── ErrorResponse.java               # Formato estructurado de errores
    │   │   │   ├── GenerarMultaRequest.java         # Record de solicitud con validaciones
    │   │   │   ├── GlobalExceptionHandler.java      # Manejo global de excepciones (@RestControllerAdvice)
    │   │   │   └── MultaController.java             # Controlador REST (/api/multas)
    │   │   │
    │   │   ├── domain/                              # Núcleo de Abstracción de Pagos (Dominio Puro)
    │   │   │   ├── PagoRechazadoException.java      # Excepción de dominio para fallos de pasarela
    │   │   │   ├── ResultadoPago.java               # Record inmutable de respuesta estandarizada
    │   │   │   └── port/
    │   │   │       └── PasarelaPagoPort.java        # Puerto saliente / Interfaz agnóstica de pagos
    │   │   │
    │   │   ├── infrastructure/                      # Adaptadores y Componentes Técnicos
    │   │   │   ├── config/
    │   │   │   │   └── RestTemplateConfig.java      # Bean RestTemplate para comunicación HTTP
    │   │   │   └── pago/
    │   │   │       ├── PagosUdesAdapter.java        # Adaptador para pasarela institucional PagosUDES
    │   │   │       └── WompiAdapter.java            # Adaptador para pasarela comercial Wompi
    │   │   │
    │   │   ├── model/                               # Dominio de Entidades y Reglas de Negocio
    │   │   │   ├── EstadoMulta.java                 # Enum de estados de la multa
    │   │   │   ├── LimiteMultasPendientesException.java # Excepción por exceso de 3 multas
    │   │   │   ├── Multa.java                       # Entidad JPA con modelo rico
    │   │   │   ├── MultaNotFoundException.java      # Excepción 404
    │   │   │   └── MultaYaPagadaException.java      # Excepción 409
    │   │   │
    │   │   ├── repository/                          # Capa de Persistencia
    │   │   │   └── MultaRepository.java             # Repositorio Spring Data JPA con query optimizada
    │   │   │
    │   │   └── service/                             # Capa de Aplicación
    │   │       └── MultaService.java                # Orquestador transaccional de casos de uso
    │   │
    │   └── resources/
    │       └── application.properties               # Configuración H2 y pasarelas de pago
    │
    └── test/
        └── java/com/example/multas/
            ├── MultaControllerTest.java             # Pruebas MockMvc de endpoints y códigos HTTP
            ├── MultaServiceTest.java                # Pruebas unitarias de lógica y casos límite
            └── PasarelaPagoIntegrationTest.java     # Pruebas de integración, MockRestServiceServer y 402
```

---

## 3. Instrucciones de Ejecución y Pruebas

### 3.1 Requisitos Previos
- **Java Development Kit (JDK):** Versión 17 o superior.
- **Apache Maven:** 3.6+ (o el wrapper `mvnw`).

### 3.2 Ejecución de la Suite de Pruebas Automatizadas
Para ejecutar la totalidad de las pruebas unitarias y de integración (28 pruebas que validan todas las reglas y códigos de estado):
```bash
cd multas-biblioteca-api
mvn test
```

### 3.3 Ejecución de la Aplicación en Modo Desarrollo
Para iniciar la API REST en el puerto `8080`:
```bash
cd multas-biblioteca-api
mvn spring-boot:run
```

- **Consola H2 en memoria:** `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:multas_biblioteca_db`, Usuario: `sa`, Contraseña: *(vacía)*).
- **Endpoint Base:** `http://localhost:8080/api/multas`.

---

## 4. Justificación Exhaustiva de los 4 Puntos de Decisión de Diseño

### Punto 1: Cálculo del Monto en la Entidad vs. Service (Modelo Rico vs. Modelo Anémico)
* **Decisión:** La lógica de cálculo tarifario (`Multa.calcularMonto(int diasAtraso)` con tarifa de `$500/día` y tope de `$15.000`), así como la transición de estado (`multa.marcarComoPagada(metodo)`), residen directamente en la clase `Multa`.
* **Fundamento Teórico:** En Domain-Driven Design (DDD) y orientación a objetos pura, una entidad no debe ser una simple estructura de datos pasiva con *getters* y *setters* (antipatrón *Anemic Domain Model*). Dado que el cálculo de la multa depende exclusivamente de variables intrínsecas (número de días y constantes tarifarias de la regla) sin requerir acceso a bases de datos ni servicios externos, situarlo en la entidad garantiza alta cohesión, encapsulamiento y reusabilidad universal, previniendo duplicidad de reglas en diferentes servicios o controladores.

---

### Punto 2: Conteo de Multas Pendientes — SQL (`COUNT`) vs. Procesamiento en Memoria (Streams)
* **Decisión:** Se definió el método derivado `long countByEstudianteIdAndEstado(String estudianteId, EstadoMulta estado);` en `MultaRepository`.
* **Fundamento Teórico y de Rendimiento:**
  * **Enfoque en memoria:** Traer todas las multas del estudiante a la JVM mediante `findByEstudianteId(...)` y filtrarlas con Java Streams (`.filter(...).count()`) requiere una transferencia de datos $O(N)$ por la red, instanciación de objetos en el heap y posterior recolección de basura (*Garbage Collection overhead*).
  * **Enfoque delegado a SQL:** Al invocar `countByEstudianteIdAndEstado`, Hibernate genera una sentencia `SELECT COUNT(id) FROM multas WHERE estudiante_id = ? AND estado = ?`. El motor relacional H2 resuelve el conteo aprovechando índices y transfiere un único valor escalar primitivo a través del driver JDBC ($O(1)$ en transferencia y consumo de memoria), maximizando el rendimiento y la escalabilidad concurrente del sistema.

---

### Punto 3: Selección del Adaptador Activo — `@ConditionalOnProperty` vs. `Map` Dinámico
* **Decisión:** Se utilizó la anotación `@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "...", matchIfMissing = ...)` sobre los adaptadores `PagosUdesAdapter` y `WompiAdapter`.
* **Fundamento Arquitectónico:**
  * El requerimiento institucional del proyecto establece que la pasarela de pago es una **decisión de despliegue/infraestructura fija por sede** (por ejemplo, Bucaramanga usa PagosUDES y Cúcuta Wompi según contratos corporativos), no una selección que el usuario elija dinámicamente en el carrito de compras.
  * La resolución mediante `@ConditionalOnProperty` evalúa la configuración en el arranque del contenedor de Spring (*Startup time*). Esto optimiza el consumo de recursos al instanciar e inyectar únicamente el bean activo necesario, permitiendo que la inyección de `PasarelaPagoPort` sea directa y unívoca en el constructor de `MultaService`, reduciendo la complejidad ciclomática y evitando resoluciones por diccionario o cadenas (*String matching*) en cada transacción de pago.

---

### Punto 4: Diseño del Puerto de Dominio y Modelo Neutral de Respuesta
* **Decisión:** El puerto `PasarelaPagoPort` y el record `ResultadoPago` residen en el paquete `com.example.multas.domain` con **cero importaciones de Spring Framework, Apache HTTP o DTOs propietarios de terceros**.
* **Fundamento Teórico:**
  * **Principio de Inversión de Dependencias (DIP) y Puertos y Adaptadores:** El núcleo del negocio no debe conocer los detalles técnicos ni los contratos propietarios de los proveedores externos (como `idTransaccion` de PagosUDES o el objeto `data.reference` y centavos de Wompi).
  * **Contrato Agnóstico:** `ResultadoPago(proveedor, exitoso, referenciaExterna, mensaje)` establece un modelo canónico neutral. Los adaptadores son los encargados de traducir los esquemas JSON propietarios hacia el contrato del puerto. Si en el futuro se reemplaza Wompi por Stripe, PayPal o PSE, el dominio y los servicios permanecen completamente inmutables (*Open/Closed Principle*).

---

## 5. Análisis Comparativo de Trade-offs (Opción B vs. Opción C)

En la Parte 2 del proyecto se analizó la diferencia entre una implementación clásica del patrón Strategy en la capa de servicios (Opción B) y una arquitectura formal de Puertos y Adaptadores (Opción C). A continuación se presenta el balance técnico de costos y beneficios:

| Criterio Arquitectónico | Opción B: Strategy en `com.example.multas.service` | Opción C: Puertos y Adaptadores (`domain` + `infrastructure`) [Implementada] |
| :--- | :--- | :--- |
| **Separación de Responsabilidades** | Media. Las estrategias suelen convivir cerca de la lógica de negocio y pueden terminar absorbiendo detalles HTTP (`RestTemplate`, Jackson). | **Excelente.** Aislamiento físico estricto: el puerto vive en el dominio puro y los adaptadores en la capa de infraestructura. |
| **Cumplimiento del DIP (Clean Arch)** | Parcial. El servicio depende de una interfaz local, pero no existe una barrera explícita contra dependencias de framework. | **Total.** El núcleo de dominio no tiene ninguna dependencia de frameworks, librerías HTTP o bases de datos. |
| **Costo en Complejidad y Archivos** | **Bajo.** Menor cantidad de paquetes y clases intermedias. Ideal para proyectos monolíticos pequeños o MVPs rápidos. | **Moderado.** Requiere crear paquetes separados (`domain`, `infrastructure`), DTOs internos de pasarela y mappers/traductores. |
| **Facilidad de Mantenimiento y Sustitución** | Moderada. Modificar una integración externa puede requerir tocar paquetes de servicio. | **Alta.** Agregar o sustituir un proveedor de pagos es tan simple como añadir un nuevo `@Component` en `infrastructure/pago/` sin tocar una sola línea del servicio ni del dominio. |
| **Testabilidad Aislada** | Requiere mocks de la interfaz de estrategia dentro del contexto de servicio. | **Óptima.** El dominio se prueba con tests unitarios puros sin levantar el contexto de Spring; los adaptadores se prueban con `MockRestServiceServer`. |

**Conclusión del Trade-off:** Aunque la Opción C introduce una estructura de paquetes más granular, el retorno en desacoplamiento, mantenibilidad y robustez ante cambios en proveedores externos justifica plenamente su adopción para sistemas empresariales e institucionales sostenibles.

---

## 6. Resumen de Endpoints de la API REST

| Método | Endpoint | Descripción | Código Éxito | Códigos de Error |
| :--- | :--- | :--- | :---: | :--- |
| `GET` | `/api/multas` | Lista todas las multas registradas en el sistema | `200 OK` | `500` |
| `GET` | `/api/multas/{id}` | Consulta una multa específica por su ID | `200 OK` | `404 Not Found` |
| `GET` | `/api/multas/estudiante/{id}` | Consulta el historial de multas de un estudiante | `200 OK` | `500` |
| `POST` | `/api/multas` | Genera una nueva multa validando límite de 3 pendientes | `201 Created` | `400 Bad Request`, `409 Conflict` |
| `PATCH` | `/api/multas/{id}/pagar` | Realiza el pago físico en ventanilla universitaria | `200 OK` | `404 Not Found`, `409 Conflict` |
| `POST` | `/api/multas/{id}/pagar-en-linea` | Procesa el pago electrónico mediante la pasarela activa | `200 OK` | `404 Not Found`, `409 Conflict`, `402 Payment Required` |

---

## 7. Conclusiones Profesionales

1. **Evolución Arquitectónica Guiada por Principios:** La implementación demuestra cómo la arquitectura de software no es un fin en sí mismo, sino una serie de decisiones conscientes para mitigar el acoplamiento y facilitar el cambio continuo.
2. **Dominio Protegido y Expresivo:** La preservación de un modelo de dominio rico (`Multa`) y un puerto de pagos agnóstico (`PasarelaPagoPort`) asegura que las reglas de negocio universitarias permanezcan inalteradas ante fluctuaciones en proveedores tecnológicos externos.
3. **Calidad y Verificabilidad Integral:** La suite de pruebas automatizadas (JUnit 5, Mockito, MockMvc, MockRestServiceServer) provee una cobertura exhaustiva del 100% de los flujos críticos, garantizando que tanto la lógica de negocio como los contratos HTTP cumplan con los estándares de la industria.
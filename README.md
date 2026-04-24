# NanoBank Ledger — MVP

**ID de Evaluación:** FS-SR-2026-002

---

## Stack Tecnológico

| Capa | Tecnología | Justificación |
|------|-----------|---------------|
| Backend | Java 21, Spring Boot 3.4.4 | Versión LTS con soporte de Records, Virtual Threads y ecosistema maduro de seguridad y JPA |
| Frontend | Angular 19 (Standalone, Signals) | Obligatorio por el enunciado; Signals es el modelo de reactividad moderno de Angular |
| Base de Datos | H2 (in-memory) | Ver sección siguiente |
| Auth | JWT stateless (jjwt 0.12.3) | Sin estado de sesión en servidor; escala horizontalmente |
| Drag & Drop | @angular/cdk/drag-drop | Librería oficial Angular, sin dependencias externas |
| Tests BE | JUnit 5 + Mockito | Estándar incluido en Spring Boot Starter Test |
| Tests FE | Jasmine + Karma | Incluido en Angular CLI sin configuración adicional |

---

## Prerrequisitos

| Herramienta | Versión mínima |
|-------------|----------------|
| Java JDK | 21 |
| Node.js | 18+ |
| npm | 9+ |
| Maven | No requerido — se usa el wrapper `mvnw` incluido |

---

## Justificación de Base de Datos: H2 In-Memory

**Decisión:** H2 in-memory sobre PostgreSQL/MySQL para este MVP.

**Razones técnicas:**
1. **Cero fricción de infraestructura**: El evaluador puede clonar y ejecutar con `./mvnw spring-boot:run` sin instalar nada adicional.
2. **Schema reproducible**: `ddl-auto=create-drop` garantiza estado limpio en cada arranque y en cada test.
3. **Portabilidad de tests**: Los tests unitarios no necesitan base de datos externa ni contenedores Docker.
4. **Migración trivial a producción**: Spring Data JPA es agnóstico al motor. Cambiar a PostgreSQL solo requiere:
   - Añadir el driver `postgresql` en `pom.xml`
   - Actualizar `application.properties` con la URL de conexión
   - Cambiar `H2Dialect` → `PostgreSQLDialect`
   - Añadir `Flyway` o `Liquibase` para migraciones versionadas

**Por qué NO usar PostgreSQL en MVP:** Añadiría complejidad operacional sin valor diferencial en esta fase de evaluación.

---

## Decisiones de Arquitectura y Modelo de Datos

### Relación User → Wallet: OneToMany (no OneToOne)

El enunciado pide "Crear y listar diferentes Billeteras (ej. Ahorros, Gastos, Inversiones)". Esto implica **múltiples billeteras por usuario**. Se eligió `@OneToMany` deliberadamente — un modelo `@OneToOne` viola el requisito funcional y no escala.

### BigDecimal para valores monetarios

`double` y `float` tienen errores de representación en base 2 que provocan inconsistencias en operaciones financieras (ej: `0.1 + 0.2 ≠ 0.3`). `BigDecimal` garantiza precisión exacta, que es no negociable en sistemas financieros.

### JWT Stateless

Se eligió JWT sobre sesiones de servidor porque:
- No requiere almacenamiento de sesión ni Redis
- Escala horizontalmente sin afinidad de sesión
- El token transporta la identidad del usuario (email como subject del claim)

### `@Transactional` en la capa de servicios

Todos los métodos que modifican datos están anotados con `@Transactional`. Esto garantiza que si una operación falla a mitad de ejecución (ej: se debita la wallet origen pero falla el crédito en destino), la base de datos hace rollback automático y queda en estado consistente. Los métodos de solo lectura usan `@Transactional(readOnly = true)` para optimizar el uso de conexiones.

### Validación de fondos ANTES de modificar entidades

En `TransactionService.transfer()` y `applyBalance()`, la validación de saldo insuficiente ocurre **antes** de cualquier modificación. Si se validara después, un fallo posterior dejaría estado inconsistente incluso dentro de una transacción.

### DTOs como Java Records (no clases con Lombok)

Los `record` de Java 16+ son inmutables por diseño, eliminan boilerplate sin dependencias externas (Lombok es opcional), y son más legibles en code review.

### `move()` vs `transfer()` para el Drag & Drop

- **`transfer()`** — crea dos transacciones nuevas (EXPENSE en origen, INCOME en destino). Simula una transferencia bancaria.
- **`move()`** — reasigna una transacción **existente** a otra billetera, revirtiendo el efecto en el origen y aplicándolo en el destino. Es el comportamiento correcto para drag & drop: no duplica registros.

---

## Estructura del Proyecto

```
nanobank-ledger/
├── README.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/nanobank/backend/
│       │   │   ├── BackendApplication.java
│       │   │   ├── controller/
│       │   │   │   ├── AuthController.java          # POST /api/auth/register|login
│       │   │   │   ├── TransactionController.java   # POST|GET /api/transactions
│       │   │   │   └── WalletController.java        # POST|GET /api/wallets
│       │   │   ├── dto/
│       │   │   │   ├── AuthResponse.java            # Salida: token + userId + username
│       │   │   │   ├── LoginRequest.java            # Entrada: email + password
│       │   │   │   ├── MoveTransactionRequest.java  # Entrada: transactionId + targetWalletId
│       │   │   │   ├── RegisterRequest.java         # Entrada: username + email + password
│       │   │   │   ├── TransactionRequest.java      # Entrada: amount + type + category + walletId
│       │   │   │   ├── TransactionResponse.java     # Salida: id + amount + type + category + date
│       │   │   │   ├── TransferRequest.java         # Entrada: fromWalletId + toWalletId + amount
│       │   │   │   ├── WalletRequest.java           # Entrada: name
│       │   │   │   └── WalletResponse.java          # Salida: id + name + balance
│       │   │   ├── entity/
│       │   │   │   ├── Transaction.java             # @Entity: movimiento financiero
│       │   │   │   ├── TransactionType.java         # @Enum: INCOME | EXPENSE
│       │   │   │   ├── User.java                    # @Entity: usuario del sistema
│       │   │   │   └── Wallet.java                  # @Entity: billetera con saldo
│       │   │   ├── exception/
│       │   │   │   ├── ErrorResponse.java           # Record: estructura uniforme de error HTTP
│       │   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice centralizado
│       │   │   │   ├── InsufficientFundsException.java
│       │   │   │   └── ResourceNotFoundException.java
│       │   │   ├── repository/
│       │   │   │   ├── TransactionRepository.java   # findByWalletId + filtros por categoría y fecha
│       │   │   │   ├── UserRepository.java          # findByEmail + existsByEmail/Username
│       │   │   │   └── WalletRepository.java        # findByUserId
│       │   │   ├── security/
│       │   │   │   ├── JwtAuthFilter.java           # OncePerRequestFilter: extrae y valida JWT
│       │   │   │   ├── JwtService.java              # Genera y valida tokens HMAC-SHA256
│       │   │   │   └── SecurityConfig.java          # CORS + rutas públicas + filtros
│       │   │   └── service/
│       │   │       ├── AuthService.java             # register() + login()
│       │   │       ├── TransactionService.java      # create() + transfer() + move() + filtros
│       │   │       ├── UserService.java             # implements UserDetailsService
│       │   │       └── WalletService.java           # create() + findByUser() + findById()
│       │   └── resources/
│       │       └── application.properties
│       └── test/
│           └── java/com/nanobank/backend/
│               ├── BackendApplicationTests.java     # Context load test
│               └── service/
│                   ├── AuthServiceTest.java         # 5 tests
│                   ├── TransactionServiceTest.java  # 15 tests
│                   └── WalletServiceTest.java       # 6 tests
└── frontend/
    ├── package.json
    ├── angular.json
    ├── tsconfig.json
    └── src/
        └── app/
            ├── app.component.ts                     # Raíz: solo <router-outlet>
            ├── app.config.ts                        # HttpClient + interceptor + router
            ├── app.routes.ts                        # Rutas lazy + authGuard
            ├── components/
            │   ├── dashboard/
            │   │   ├── dashboard.component.ts       # Signals + CDK DnD + OnPush
            │   │   ├── dashboard.component.html
            │   │   └── dashboard.component.scss
            │   └── login/
            │       ├── login.component.ts           # Login/Register con signals
            │       ├── login.component.html
            │       └── login.component.scss
            ├── guards/
            │   └── auth.guard.ts                    # Redirige a /login si no hay token
            ├── interceptors/
            │   └── auth.interceptor.ts              # Inyecta Bearer token en cada request
            ├── models/
            │   ├── auth.model.ts                    # LoginRequest, RegisterRequest, AuthResponse
            │   ├── transaction.model.ts             # Transaction, TransactionRequest, TransferRequest, MoveTransactionRequest
            │   └── wallet.model.ts                  # Wallet, WalletRequest
            └── services/
                ├── auth.service.ts                  # login() + register() + logout() + signal isAuthenticated
                ├── auth.service.spec.ts             # 5 tests con HttpTestingController
                ├── transaction.service.ts           # getByWallet() + create() + transfer() + move()
                └── wallet.service.ts                # getAll() + getById() + create()
```

---

## Principios SOLID Aplicados

**S — Single Responsibility:**
Cada clase tiene una única razón de cambio. `WalletService` contiene lógica de billeteras; no conoce HTTP ni JPA directamente. `WalletController` recibe HTTP y delega; no contiene lógica de negocio.

**O — Open/Closed:**
`GlobalExceptionHandler` centraliza el manejo de errores. Añadir una nueva excepción custom agrega un método nuevo; no modifica los existentes.

**L — Liskov Substitution:**
`UserService` implementa `UserDetailsService` de Spring Security sin alterar el contrato esperado. Spring puede usar cualquier `UserDetailsService` y el comportamiento es correcto.

**I — Interface Segregation:**
Los repositorios heredan de `JpaRepository<T, ID>` y declaran únicamente los métodos necesarios (`findByUserId`, `findByWalletId`). No se hereda una interfaz monolítica con métodos que no se usan.

**D — Dependency Inversion:**
Los servicios y controladores dependen de abstracciones (interfaces de repositorio), no de implementaciones concretas. Spring inyecta la implementación en runtime.

---

## Endpoints API

### Auth (público)
| Método | Ruta | Body | Respuesta |
|--------|------|------|-----------|
| POST | `/api/auth/register` | `{username, email, password}` | `{token, userId, username}` |
| POST | `/api/auth/login` | `{email, password}` | `{token, userId, username}` |

### Wallets (requieren `Authorization: Bearer <token>`)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/api/wallets` | Billeteras del usuario autenticado |
| POST | `/api/wallets` | Crear nueva billetera |
| GET | `/api/wallets/{id}` | Obtener billetera por ID |

### Transactions (requieren JWT)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/api/transactions` | Crear transacción (actualiza saldo de la billetera) |
| POST | `/api/transactions/transfer` | Transferir saldo entre dos billeteras |
| POST | `/api/transactions/move` | Mover transacción existente a otra billetera (Drag & Drop) |
| GET | `/api/transactions?walletId=&category=&startDate=&endDate=` | Listar con filtros opcionales |

---

## Cómo Ejecutar

**Backend (puerto 8080):**
```bash
cd backend

# Linux / Mac / Git Bash
./mvnw spring-boot:run

# Windows CMD
mvnw.cmd spring-boot:run
```

**Frontend (puerto 4200):**
```bash
cd frontend
npm install
ng serve
```

**Inspección de base de datos (H2 Console):**
```
URL:      http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:nanobankdb
Usuario:  sa
Password: (vacío)
```

---

## Tests y Cobertura

**Ejecutar tests:**
```bash
# Backend — 27 tests unitarios
cd backend && ./mvnw test

# Frontend — 9 tests (con reporte de cobertura en coverage/)
cd frontend && ng test --watch=false --browsers=ChromeHeadless --code-coverage
```

**Resultados verificados:**

| Suite | Tests | Resultado |
|-------|-------|-----------|
| `BackendApplicationTests` | 1 | ✅ PASS |
| `AuthServiceTest` | 5 | ✅ PASS |
| `TransactionServiceTest` | 15 | ✅ PASS |
| `WalletServiceTest` | 6 | ✅ PASS |
| `AuthServiceSpec` (Angular) | 9 | ✅ PASS |
| **Total** | **36** | **0 fallos** |

**Cobertura alcanzada:** ≥ 80% en la capa de servicios de ambas capas, cumpliendo el requisito obligatorio del enunciado.

**Qué se prueba y por qué:**
- `WalletServiceTest`: ownership check — un usuario no puede acceder ni operar wallets ajenas
- `TransactionServiceTest`: lógica crítica de cálculo de saldos, fondos insuficientes, ownership en transacciones, y correcta reversión de balances al mover transacciones entre billeteras
- `AuthServiceTest`: registro con email/username duplicado, credenciales inválidas, mensaje de error controlado
- `AuthServiceSpec`: almacenamiento de token en localStorage, estado del signal `isAuthenticated`, limpieza en logout

---

## Uso Estratégico de IA

La IA se utilizó como **acelerador de productividad** para tareas específicas y bien definidas. En ningún caso se aceptaron sugerencias sin revisión técnica.

### Rol del desarrollador
- Diseño de la arquitectura y decisiones técnicas principales
- Escritura de especificaciones técnicas precisas como entrada a la IA
- Revisión crítica de todo el código generado
- Identificación y corrección de errores, vulnerabilidades y antipatrones
- Validación de que el código cumple SOLID y los requisitos funcionales

### Correcciones aplicadas al código generado

**Corrección 1 — Modelo de dominio `User → Wallet`**

La IA generó `@OneToOne`. Se rechazó porque el enunciado requiere múltiples billeteras por usuario. Se reemplazó por `@OneToMany` / `@ManyToOne`.

**Corrección 2 — Decodificación del secret JWT**

La IA usó `Decoders.BASE64.decode(secret)` asumiendo que el secret en `application.properties` era Base64. Esto lanza `DecodingException` en runtime con texto plano. Se corrigió a `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`, que acepta cualquier string de al menos 32 caracteres.

**Corrección 3 — `TransactionService.create()` retornaba `null`**

El código generado tenía `return null` como placeholder. Se completó la lógica: guardar la entidad, mapear a DTO, retornar la respuesta. Un `null` aquí causa `NullPointerException` en el frontend.

**Corrección 4 — Validación de fondos en `transfer()`**

El código generado validaba fondos después de modificar el balance origen. Se movió la validación al inicio del método para evitar estados inconsistentes, aunque estén dentro de `@Transactional`.

**Corrección 5 — Signals vs Observables en Angular**

La IA mezcló `Observable + async pipe` con `signal()`, creando doble fuente de verdad. Se unificó usando solo signals, suscribiendo el Observable y actualizando el signal con `.set()`. Esto es necesario para que `ChangeDetectionStrategy.OnPush` funcione correctamente.

**Corrección 6 — `cdkDropListConnectedTo` hardcodeado**

La IA generó un array literal con los IDs de las wallets. Se reemplazó por `walletIds = computed(() => wallets().map(...))` para que sea reactivo cuando se añaden nuevas wallets dinámicamente.

**Corrección 7 — Falta de ownership en `TransactionService`**

La IA generó `create()`, `transfer()` y `findByFilters()` sin verificar que la wallet pertenezca al usuario autenticado. Cualquier usuario con un JWT válido podía operar sobre wallets ajenas enviando un `walletId` arbitrario (vulnerabilidad IDOR). Se añadió `getOwnedWalletOrThrow(walletId, userEmail)` en todos los métodos. Si la wallet no pertenece al usuario se lanza `ResourceNotFoundException` (404, no 403) para no revelar que la wallet existe.

**Corrección 8 — Drag & Drop usaba `transfer` en lugar de `move`**

La IA implementó el drag & drop llamando a `POST /api/transactions/transfer`, que crea dos transacciones nuevas (débito y crédito). Esto es incorrecto para un arrastre: la transacción original queda en la billetera de origen y se crean registros duplicados. Se creó el endpoint `POST /api/transactions/move` que reasigna la transacción existente a la billetera destino y ajusta ambos saldos mediante `revertBalance()` en origen y `applyBalance()` en destino, sin duplicar registros.

**Corrección 9 — `AuthService.login()` exponía mensajes internos de Spring Security**

La IA no envolvía la excepción de `authManager.authenticate()`. Spring Security lanza `BadCredentialsException` con el mensaje interno `"Bad credentials"`, que se propagaba directamente al cliente. Se añadió un bloque `try-catch` que captura cualquier `AuthenticationException` y relanza `BadCredentialsException("Invalid credentials")` — mensaje controlado que no expone detalles del mecanismo de autenticación interno.

**Corrección 10 — Dependencias de test inexistentes en `pom.xml`**

La IA generó artefactos de test como `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test` y `spring-boot-starter-webmvc-test`, que no existen en el repositorio de Maven y causan error de resolución de dependencias. Se reemplazaron por los artefactos estándar: `spring-boot-starter-test` (incluye JUnit 5, Mockito y AssertJ) y `spring-security-test`.

---

## Bitácora de Prompts

**Prompt 1 — Modelo de dominio JPA**
> *"Genera entidades JPA para User, Wallet y Transaction en Spring Boot 3 con Java 21. User tiene múltiples Wallets (OneToMany). Wallet tiene múltiples Transactions. Usa BigDecimal para montos monetarios, enum con EnumType.STRING para INCOME/EXPENSE, LocalDateTime para timestamps asignado en constructor. Implementa equals/hashCode basados solo en id."*

Resultado: estructura de entidades correcta. Corrección aplicada: la IA propuso `@OneToOne` entre User y Wallet — se cambió a `@OneToMany` según los requisitos del negocio.

---

**Prompt 2 — Seguridad JWT con Spring Security**
> *"Implementa autenticación JWT stateless en Spring Boot 3 con Spring Security. JwtService debe generar tokens con email como subject y validarlos. JwtAuthFilter extiende OncePerRequestFilter y extrae el token del header Authorization. SecurityConfig: CSRF off, sesión STATELESS, CORS para localhost:4200, rutas públicas /api/auth/**, BCryptPasswordEncoder."*

Resultado: stack de seguridad funcional. Correcciones: uso incorrecto de `BASE64.decode()` para el secret (corregido a `getBytes(UTF_8)`) y falta de default value en `@Value` para tests.

---

**Prompt 3 — Servicios de negocio con validaciones**
> *"Implementa TransactionService con: create() que aplica balance según tipo INCOME/EXPENSE validando fondos antes de modificar, transfer() entre dos wallets con validación de fondos insuficientes, findByFilters() con parámetros opcionales de categoría y fecha. Todo con @Transactional y excepciones custom ResourceNotFoundException e InsufficientFundsException."*

Resultado: lógica de negocio base. Correcciones: `create()` retornaba `null`, validación de fondos ocurría después de modificar balance, y faltaba ownership check en todos los métodos.

---

**Prompt 4 — Dashboard Angular 19 con Signals y Drag & Drop**
> *"Crea DashboardComponent standalone con ChangeDetectionStrategy.OnPush. Estado con signal(): wallets, selectedWallet, transactions, categoryFilter. filteredTransactions como computed() que filtra en tiempo real. Drag & Drop con @angular/cdk/drag-drop: cada wallet en sidebar es cdkDropList, cada transacción es cdkDrag. Al soltar en otra wallet llamar al backend. walletIds como computed() reactivo para cdkDropListConnectedTo."*

Resultado: componente funcional. Correcciones: IA mezcló Observables con signals (unificado a solo signals), `cdkDropListConnectedTo` era array literal (reemplazado por `computed()`), y DnD llamaba a `transfer` en lugar de `move`.

---

**Prompt 5 — Tests unitarios con JUnit 5 y Mockito**
> *"Escribe tests unitarios con @ExtendWith(MockitoExtension.class) para WalletService, TransactionService y AuthService. Cubre: cálculo de balance en INCOME y EXPENSE, InsufficientFundsException al superar saldo, ResourceNotFoundException cuando wallet no existe, ownership check cuando wallet pertenece a otro usuario, register con email/username duplicado, login con credenciales inválidas. Usa AssertJ para assertions."*

Resultado: 26 tests generados. Correcciones: los tests de `TransactionService` no pasaban `userEmail` al servicio (se añadió tras refactorizar el servicio), y faltaban casos de ownership en `transfer()`, `move()` y `findByFilters()`.

---
name: nanobank-ledger-fullstack-mvp
description: >
  Especificaciones técnicas detalladas para el MVP NanoBank Ledger.
  Estas especificaciones guían la implementación de cada capa del sistema.
  El desarrollador define la arquitectura, restricciones y criterios de calidad;
  la IA asiste en la generación de código dentro de esos límites.
parameters:
  - name: entities
    description: "User, Wallet, Transaction — ver relaciones en sección 1"
  - name: auth
    description: "JWT stateless con jjwt 0.12.x — ver sección 5"
  - name: coverage
    description: "≥80% cobertura en servicios de ambas capas — ver secciones 9 y 10"
---

# Especificaciones Técnicas — NanoBank Ledger MVP

> Estas especificaciones fueron definidas por el desarrollador senior como
> entrada para la asistencia de IA. La IA genera código dentro de estas
> restricciones; el desarrollador revisa, corrige y valida cada salida.

---

## 1. MODELO DE DOMINIO JPA

**Restricciones del desarrollador (no negociables):**
- `User → Wallet` es `OneToMany`, no `OneToOne` — el negocio requiere múltiples billeteras
- `BigDecimal` para todo valor monetario — `double`/`float` son inaceptables en finanzas
- `LocalDateTime` para timestamps — asignado en constructor, nunca en el cliente
- `equals`/`hashCode` basados solo en `id` — evita problemas con colecciones JPA

### User (`com.nanobank.backend.entity.User`)
```
id: Long (PK, auto-generated)
username: String (not null, unique)
email: String (not null, unique)
password: String (not null) → almacenado como hash BCrypt
wallets: List<Wallet> → @OneToMany(mappedBy="user", cascade=ALL, fetch=LAZY)
```

### Wallet (`com.nanobank.backend.entity.Wallet`)
```
id: Long (PK, auto-generated)
name: String (not null)
balance: BigDecimal (not null, default=ZERO) → solo modificable por TransactionService
user: User → @ManyToOne(fetch=LAZY), @JoinColumn(name="user_id", nullable=false)
transactions: List<Transaction> → @OneToMany(mappedBy="wallet", cascade=ALL, fetch=LAZY)
```

### Transaction (`com.nanobank.backend.entity.Transaction`)
```
id: Long (PK, auto-generated)
amount: BigDecimal (not null)
type: TransactionType (not null) → @Enumerated(STRING)
timestamp: LocalDateTime (not null) → LocalDateTime.now() en constructor
description: String (nullable)
category: String (nullable) → para filtros
wallet: Wallet → @ManyToOne(fetch=LAZY), @JoinColumn(name="wallet_id", nullable=false)
```

### TransactionType
```java
public enum TransactionType { INCOME, EXPENSE }
```

---

## 2. REPOSITORIOS

Solo declarar métodos realmente utilizados. Spring Data genera la implementación.

```java
// UserRepository extends JpaRepository<User, Long>
Optional<User> findByEmail(String email);
boolean existsByEmail(String email);
boolean existsByUsername(String username);

// WalletRepository extends JpaRepository<Wallet, Long>
List<Wallet> findByUserId(Long userId);

// TransactionRepository extends JpaRepository<Transaction, Long>
List<Transaction> findByWalletId(Long walletId);
List<Transaction> findByWalletIdAndCategory(Long walletId, String category);
List<Transaction> findByWalletIdAndTimestampBetween(Long walletId, LocalDateTime start, LocalDateTime end);
```

---

## 3. DTOs (Java Records — no Lombok, no clases mutables)

```java
// Entrada
record WalletRequest(@NotBlank String name)
record TransactionRequest(String description, @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String type, String category, @NotNull Long walletId)
record TransferRequest(@NotNull Long fromWalletId, @NotNull Long toWalletId,
    @NotNull @DecimalMin("0.01") BigDecimal amount, String description)
record LoginRequest(@NotBlank String email, @NotBlank String password)
record RegisterRequest(@NotBlank String username, @NotBlank @Email String email,
    @NotBlank @Size(min=6) String password)

// Salida (nunca exponer entidades JPA directamente)
record WalletResponse(Long id, String name, BigDecimal balance)
record TransactionResponse(Long id, String description, BigDecimal amount,
    String type, String category, LocalDateTime date, Long walletId)
record AuthResponse(String token, Long userId, String username)
```

---

## 4. MANEJO DE EXCEPCIONES

```
ResourceNotFoundException extends RuntimeException → HTTP 404
InsufficientFundsException extends RuntimeException → HTTP 422
record ErrorResponse(int status, String message, LocalDateTime timestamp)

@RestControllerAdvice GlobalExceptionHandler:
  ResourceNotFoundException     → 404
  InsufficientFundsException    → 422
  MethodArgumentNotValidException → 400 (campos concatenados)
  IllegalArgumentException      → 400
  Exception (fallback)          → 500
```

---

## 5. SEGURIDAD JWT

**Decisión del desarrollador:** JWT stateless — no requiere sesiones en servidor.

### JwtService
- Secret: `@Value("${app.jwt.secret:default-32-char-secret-for-tests-only}")`
- **IMPORTANTE:** usar `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` — NO `BASE64.decode()` (el secret es texto plano)
- `generateToken(email)`: subject=email, exp=`${app.jwt.expiration}` ms
- `isTokenValid(token, email)`: verificar subject + no expirado

### JwtAuthFilter (OncePerRequestFilter)
- Extraer `Authorization: Bearer <token>`
- Validar → setear en `SecurityContextHolder`
- Errores: ignorar silenciosamente (no propagar excepción)

### SecurityConfig
- CSRF: off | Sesión: STATELESS
- CORS: origen `http://localhost:4200`, métodos HTTP todos, headers `*`
- Públicas: `/api/auth/**`, `/h2-console/**`
- `frameOptions.disable()` para H2 console en desarrollo
- Beans: `PasswordEncoder` (BCrypt), `AuthenticationManager`

### Dependencias pom.xml
```xml
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.3</version></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.3</version><scope>runtime</scope></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.3</version><scope>runtime</scope></dependency>
```

---

## 6. SERVICIOS — REGLAS DE NEGOCIO

**Regla del desarrollador:** Toda lógica de negocio en servicios. Controladores solo delegan.

### AuthService
```
register: validar email único → validar username único → hashear password → guardar → JWT
login: autenticar con AuthenticationManager → buscar usuario → JWT
```

### WalletService
```
create(request, userEmail): buscar usuario por email → crear wallet con saldo ZERO → guardar
findByUser(userEmail): buscar usuario → wallets por userId
findById(id, userEmail): buscar wallet → verificar owner.email == userEmail → lanzar 404 si no coincide
```

### TransactionService — CRÍTICO
```
applyBalance(wallet, amount, type) [privado]:
  → INCOME: balance += amount
  → EXPENSE: SI balance < amount → InsufficientFundsException; SINO balance -= amount
  → VALIDAR SIEMPRE ANTES de modificar (no después)

create(request):
  1. buscar wallet (404 si no existe)
  2. parsear type → IllegalArgumentException si inválido
  3. applyBalance (puede lanzar InsufficientFundsException)
  4. crear Transaction
  5. guardar wallet (balance actualizado)
  6. guardar transaction
  7. retornar DTO mapeado

transfer(request):
  1. buscar fromWallet (404 si no existe)
  2. buscar toWallet (404 si no existe)
  3. VALIDAR: fromWallet.balance >= amount (antes de modificar)
  4. from.balance -= amount
  5. to.balance += amount
  6. crear Transaction EXPENSE en from
  7. crear Transaction INCOME en to
  8. guardar ambas wallets
  9. guardar ambas transactions
  10. retornar DTO del crédito
```

---

## 7. CONTROLADORES REST

```
POST   /api/auth/register      @Valid body → 201 AuthResponse
POST   /api/auth/login         @Valid body → 200 AuthResponse

GET    /api/wallets            Authentication.getName() como userEmail → 200 List<WalletResponse>
POST   /api/wallets            @Valid body + Authentication → 201 WalletResponse
GET    /api/wallets/{id}       @PathVariable + Authentication → 200 WalletResponse

POST   /api/transactions       @Valid body → 201 TransactionResponse
POST   /api/transactions/transfer  @Valid body → 201 TransactionResponse
GET    /api/transactions       @RequestParam walletId, category?, startDate?, endDate? → 200 List
```

---

## 8. CONFIGURACIÓN

```properties
# H2 in-memory
spring.datasource.url=jdbc:h2:mem:nanobankdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true

# JWT
app.jwt.secret=nanobank-ledger-2026-secret-key-min-256-bits-required
app.jwt.expiration=86400000
```

---

## 9. TESTS BACKEND (JUnit 5 + Mockito)

**Criterio del desarrollador:** Probar la lógica de negocio crítica, no el framework.

### WalletServiceTest — casos obligatorios
```
create → usuario existe → OK
create → usuario no existe → ResourceNotFoundException
findByUser → retorna wallets del usuario
findById → wallet existe y es del usuario → OK
findById → wallet no existe → ResourceNotFoundException
findById → wallet de otro usuario → ResourceNotFoundException (ownership)
```

### TransactionServiceTest — casos obligatorios
```
create INCOME → balance += amount (verificar en memoria)
create EXPENSE → balance -= amount
create EXPENSE saldo insuficiente → InsufficientFundsException
create wallet no existe → ResourceNotFoundException
create tipo inválido → IllegalArgumentException
transfer → from.balance -= y to.balance += (verificar ambos)
transfer saldo insuficiente → InsufficientFundsException
findByFilters con categoría → delega a findByWalletIdAndCategory
findByFilters sin filtros → delega a findByWalletId
```

### AuthServiceTest — casos obligatorios
```
register usuario nuevo → AuthResponse con token
register email duplicado → IllegalArgumentException
register username duplicado → IllegalArgumentException
login credenciales válidas → AuthResponse con token
```

---

## 10. TESTS FRONTEND (Jasmine + Angular TestBed)

### AuthServiceSpec — casos obligatorios
```
debería crearse el servicio
login → guarda token en localStorage + isAuthenticated = true
register → guarda token en localStorage + isAuthenticated = true
logout → limpia localStorage + isAuthenticated = false
getToken → retorna token guardado
getToken → retorna null si no hay token
```

**Setup obligatorio:**
```typescript
providers: [AuthService, provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
// beforeEach: localStorage.clear()
// afterEach: httpMock.verify() + localStorage.clear()
```

---

## 11. FRONTEND ANGULAR 19

**Restricciones del desarrollador:**
- Standalone Components obligatorio (no NgModules)
- Signals para todo el estado reactivo — NO mezclar con Observables/async pipe
- `ChangeDetectionStrategy.OnPush` en Dashboard
- `computed()` para filtros derivados — no filtrar en template ni en subscribe

### Arquitectura de estado en DashboardComponent
```typescript
// Signals de datos
wallets = signal<Wallet[]>([]);
selectedWallet = signal<Wallet | null>(null);
transactions = signal<Transaction[]>([]);

// Signals de filtros
categoryFilter = signal<string>('');

// Derivado reactivo — NO hacer esto en el subscribe
filteredTransactions = computed(() => {
  const cat = categoryFilter().toLowerCase();
  return cat ? transactions().filter(t => t.category?.toLowerCase().includes(cat)) : transactions();
});

// IDs para CDK DnD — DEBE ser computed, no literal hardcodeado
walletIds = computed(() => wallets().map(w => `wallet-${w.id}`));
```

### Drag & Drop — flujo
```
1. Usuario arrastra una Transaction card
2. Suelta sobre un Wallet diferente en el sidebar
3. onTransactionDrop(event, targetWallet) se ejecuta
4. Verificar: transaction.walletId !== targetWallet.id
5. Llamar transactionService.transfer({fromWalletId, toWalletId, amount})
6. En subscribe next: recargar wallets (saldos actualizados) + recargar transactions
```

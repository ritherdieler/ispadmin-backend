---
name: mobile-best-practices
description: >-
  Guías de arquitectura móvil Clean Architecture (presentation/domain/data), estados
  de UI, rendimiento y almacenamiento seguro de credenciales. Usar al implementar o
  revisar apps Android (Kotlin/Compose), Flutter o React Native: ViewModels, repositorios,
  listas, imágenes, tokens y flujos offline/online.
---

# Mobile best practices

## Clean Architecture

Capas obligatorias:

| Capa | Responsabilidad | Ejemplos |
|------|-----------------|----------|
| `presentation` | UI + estado de pantalla | Compose/Screens, ViewModel, UiState |
| `domain` | reglas de negocio puras | UseCase, entities, repository interfaces |
| `data` | I/O | Repository impl, API, DB, mappers DTO↔domain |

```text
app/
  presentation/feature/dashboard/
  domain/usecase/GetDashboardUseCase.kt
  domain/repository/DashboardRepository.kt
  data/repository/DashboardRepositoryImpl.kt
  data/remote/DashboardApi.kt
```

Reglas:

- `presentation` no conoce Retrofit/Room/SharedPreferences.
- `domain` no importa frameworks de UI ni HTTP.
- Controllers/API del backend exponen DTOs; la app mapea a modelos de dominio.

## Estados de UI

Modelar estados de forma exhaustiva (sealed / union):

```kotlin
sealed interface DashboardUiState {
  data object Loading : DashboardUiState
  data object Empty : DashboardUiState
  data class Success(val data: Dashboard) : DashboardUiState
  data class Error(val message: String, val retryable: Boolean = true) : DashboardUiState
}
```

```ts
// React Native / Flutter-equivalent pattern
type UiState<T> =
  | { status: 'loading' }
  | { status: 'empty' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string; retryable?: boolean }
```

- Un solo `UiState` por pantalla/flujo; evitar booleans sueltos (`isLoading` + `hasError` + `data?`).
- La UI renderiza por `when`/`switch` exhaustivo.
- Errores de red vs negocio distinguibles para el usuario.

## Rendimiento móvil

- Listas grandes: `LazyColumn` / `RecyclerView` / `FlashList` con keys estables; evitar recomposición/bind pesado por ítem.
- Imágenes: tamaño adecuado, cache (Coil/Glide/FastImage), placeholders; no decodificar full-res en listas.
- Trabajo pesado fuera del main thread (coroutines/`Dispatchers.Default`, isolates, workers).
- Red: timeouts, paginación, backoff; no spamear endpoints en recomposición.
- Batería: minimizar localisation/sensors en foreground service; cancelar jobs al destruir el ViewModel.
- Evitar re-fetch en cada recomposición: `StateFlow`/`collectAsStateWithLifecycle`, cached queries.

```kotlin
// ✅ Collect con lifecycle consciente
val uiState by viewModel.state.collectAsStateWithLifecycle()
```

## Credenciales y tokens

- Access/refresh tokens solo en **EncryptedSharedPreferences** / **Keystore** / **Secure Storage** (RN) / **Keychain** (iOS) / **FlutterSecureStorage**.
- Nunca logs de tokens, ni `BuildConfig` con secretos de producción.
- Interceptor añade `Authorization: Bearer`; Authenticator refresca en `401` y reintenta una vez.
- Al fallar refresh: limpiar sesión y navegar a login.
- Cert pinning / HTTPS obligatorio en prod; cleartext solo en debug allowlist de IPs locales.

```kotlin
// ❌ Evitar
prefs.edit().putString("token", accessToken) // SharedPreferences plano

// ✅ Preferir
tokenStore.save(accessToken, refreshToken) // storage cifrado
```

## Checklist rápido

- [ ] ¿UseCase testeable sin Android framework?
- [ ] ¿UiState cubre Loading/Empty/Success/Error?
- [ ] ¿Listas virtualizadas e imágenes acotadas?
- [ ] ¿Tokens fuera de storage plano y fuera de logs?
- [ ] ¿Cancelación de corrutinas/subscriptions al salir de la pantalla?

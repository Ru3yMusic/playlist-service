# playlist-service

Microservicio de gestión de playlists de **RUBY MUSIC**. Permite crear, editar, reordenar y eliminar playlists de usuario. Maneja la playlist especial del sistema **"Tus me gusta"** (`is_system = true`), que se crea automáticamente al registrar un usuario y no puede ser eliminada ni renombrada.

---

## Responsabilidad

- CRUD completo de playlists de usuario
- Agregar, eliminar y reordenar canciones dentro de una playlist (drag & drop por `position`)
- Soft delete de playlists (soporta "Deshacer")
- Gestión de la playlist sistema `"Tus me gusta"` (idempotente, inmutable, inborrable)
- Verificación de propiedad en cada operación de escritura

---

## Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.1 |
| Spring Data JPA | — |
| MapStruct | 1.5.5.Final |
| Lombok | — |
| SpringDoc OpenAPI | 2.5.0 |
| OpenAPI Generator (Maven plugin) | 7.4.0 |

> Sin Kafka — este servicio es de solo lectura/escritura directa, sin eventos async.

---

## Puerto

| Servicio | Puerto |
|---|---|
| playlist-service | **8084** |
| Acceso vía gateway | `http://localhost:8080/api/v1/playlists` |

---

## Base de datos

| Parámetro | Valor |
|---|---|
| Engine | PostgreSQL |
| Database | `playlist_db` |
| Host | `localhost:5432` |
| DDL | `update` (Hibernate auto-schema) |

### Entidades

| Tabla | Descripción |
|---|---|
| `playlists` | Playlist del usuario con soft delete y flag de sistema |
| `playlist_songs` | Canciones de una playlist con `position` para ordenamiento |

---

## Endpoints

Las interfaces de controller se generan desde `src/main/resources/openapi.yml`. Todos los endpoints de escritura requieren el header `X-User-Id` propagado por el api-gateway.

### Playlists

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `POST` | `/` | Crear nueva playlist | `X-User-Id` |
| `GET` | `/my` | Listar playlists del usuario (excluye soft-deleted) | `X-User-Id` |
| `GET` | `/{id}` | Obtener playlist por ID | — |
| `PUT` | `/{id}` | Actualizar nombre, descripción, portada, visibilidad | `X-User-Id` |
| `DELETE` | `/{id}` | Soft delete de playlist | `X-User-Id` |

### Canciones de playlist

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| `GET` | `/{id}/songs` | Listar canciones ordenadas por `position` | — |
| `POST` | `/{id}/songs` | Agregar canción al final de la playlist | `X-User-Id` |
| `DELETE` | `/{id}/songs/{songId}` | Eliminar canción de la playlist | `X-User-Id` |
| `PUT` | `/{id}/songs/reorder` | Reordenar canciones (lista ordenada de IDs) | `X-User-Id` |

---

## Reglas de negocio

### Playlist sistema ("Tus me gusta")
- Se crea automáticamente al registrar un usuario via `createSystemPlaylist()` — es **idempotente**
- `is_system = true`, `is_public = false`
- **No puede eliminarse** — `softDelete()` lanza `IllegalStateException`
- **No puede renombrarse** — `update()` ignora el campo `name` si `is_system = true`
- Stays in sync con `song_likes` del `interaction-service` (la app agrega/elimina canciones aquí al dar/quitar like)

### Propiedad y autorización
- Toda operación de escritura verifica que `requestingUserId == playlist.userId`
- El servicio no valida JWT — confía en el `X-User-Id` header del gateway

### Ordenamiento de canciones
- Cada `PlaylistSong` tiene un campo `position` (INTEGER)
- Al agregar: `position = MAX(position) + 1` (append al final)
- Al reordenar: se actualiza el `position` de cada canción según el orden del array `orderedSongIds`

### Duplicados
- Constraint único `(playlist_id, song_id)` a nivel de BD
- `addSong()` retorna la entrada existente si la canción ya está — no lanza error
- `DataIntegrityViolationException` → `409 Conflict`

### Soft delete
- Campo `deleted_at TIMESTAMP NULLABLE` — soporta "Deshacer" desde el cliente
- `findById()` y `findByUserId()` filtran siempre por `deleted_at IS NULL`

---

## Estructura del proyecto

```
playlist-service/
├── src/
│   ├── main/
│   │   ├── java/com/rubymusic/playlist/
│   │   │   ├── PlaylistServiceApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── PlaylistsController.java       ← implements PlaylistsApi
│   │   │   │   └── PlaylistSongsController.java   ← implements PlaylistSongsApi
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── mapper/
│   │   │   │   └── PlaylistMapper.java            ← MapStruct: Playlist/PlaylistSong → DTOs
│   │   │   ├── model/
│   │   │   │   ├── Playlist.java                  ← soft delete, is_system flag
│   │   │   │   └── PlaylistSong.java              ← position field, unique(playlist_id, song_id)
│   │   │   ├── repository/
│   │   │   │   ├── PlaylistRepository.java
│   │   │   │   └── PlaylistSongRepository.java
│   │   │   └── service/
│   │   │       ├── PlaylistService.java
│   │   │       └── impl/
│   │   │           └── PlaylistServiceImpl.java
│   │   └── resources/
│   │       ├── application.yml                    ← nombre + import config-server
│   │       └── openapi.yml                        ← contrato OpenAPI 3.0.3 completo
│   └── test/
│       └── java/com/rubymusic/playlist/
│           └── PlaylistServiceApplicationTests.java
└── pom.xml
```

---

## Manejo de errores

| Excepción | HTTP | Causa típica |
|---|---|---|
| `IllegalArgumentException` | `400 Bad Request` | Playlist no encontrada o IDs inválidos |
| `IllegalStateException` | `400 Bad Request` | Intentar eliminar/renombrar playlist sistema |
| `DataIntegrityViolationException` | `409 Conflict` | Canción ya existe en la playlist |
| `NoSuchElementException` | `404 Not Found` | Recurso no encontrado |
| `Exception` (genérico) | `500 Internal Server Error` | Error inesperado |

---

## Variables de entorno

Inyectadas desde `config-server` (`config/playlist-service.yml`):

| Variable | Descripción | Default |
|---|---|---|
| `DB_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña PostgreSQL | `password` |

---

## Build & Run

```bash
# Build (genera interfaces y DTOs desde openapi.yml)
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test -Dtest=PlaylistServiceApplicationTests
```

> Requiere `discovery-service`, `config-server` y PostgreSQL en `localhost:5432` con `playlist_db` creada.
> No requiere Kafka ni Redis.

# Session user: último evento con user no vacío

## Problema

En Session Detail el resumen tomaba `user` del primer evento (`createdAt` desc). Si ese evento era un `workflow_start` previo al login (sin `userJson`), la UI mostraba **—** aunque un evento posterior (`workflow_end`) sí tuviera usuario.

## Fix

`ObsSessionQueryService.resolveSessionUser`: recorre eventos (más recientes primero) y usa el primero cuyo `userJson` parseable no sea nulo/blank.

- `getSession`: siempre usa `resolveSessionUser(allEvents)`
- `listSessions`: intenta el último evento; si no tiene user, hace fallback con la misma resolución

## Test

`ObsSessionQueryServiceTest`:
- el primer evento sin user y el siguiente con `dscorp` → summary.user = dscorp
- ningún evento con user → summary.user = null

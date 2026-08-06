---
name: react-best-practices
description: >-
  Guías de React moderno (hooks, estado, rendimiento y estructura feature-driven).
  Usar al implementar o revisar UI web con React/Next.js, componentes, custom hooks,
  Zustand/Redux Toolkit/React Query, memoización o organización de carpetas frontend.
---

# React best practices

## Principios

- Solo functional components.
- Lógica de negocio fuera del JSX: custom hooks o use cases.
- Componentes presentacionales tontos; contenedores/hooks orquestan datos.
- Tipado estricto: props y retornos explícitos; cero `any`.

## Custom hooks

```tsx
// ❌ Lógica + UI mezcladas
function InvoicesPage() {
  const [data, setData] = useState([])
  useEffect(() => { api.getInvoices().then(setData) }, [])
  return <ul>{data.map(...)}</ul>
}

// ✅ Hook con contrato claro
function useInvoices() {
  const query = useQuery({ queryKey: ['invoices'], queryFn: api.getInvoices })
  return {
    invoices: query.data ?? [],
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
  }
}
```

## Estado

| Caso | Herramienta |
|------|-------------|
| Server state (API, cache, revalidate) | React Query / TanStack Query |
| UI global ligero (tema, sesión UI) | Zustand |
| Flujos complejos con historial/devtools | Redux Toolkit |
| Estado local de un solo componente | `useState` / `useReducer` |

Reglas:

- No duplicar server state en stores globales.
- Selectores finos en Zustand/Redux para evitar re-renders.
- Mutations invalidan queries relacionadas, no mutan cache a mano salvo casos medidos.

## Optimización de renderizado

- `React.memo`, `useMemo`, `useCallback` **solo** cuando el profiler o listas grandes lo justifiquen.
- No memoizar por defecto: añade complejidad y puede empeorar rendimiento.
- Preferir: estado abajo, composición, `children` estables, virtualización.

```tsx
// ✅ Memo solo en filas caras de listas grandes
const InvoiceRow = memo(function InvoiceRow({ invoice, onSelect }: Props) {
  return <button onClick={() => onSelect(invoice.id)}>{invoice.code}</button>
})
```

- Lazy routes: `React.lazy` + `Suspense` para features pesadas.
- Evitar crear objetos/funciones inline en props de listas virtualizadas.

## Estructura feature-driven

```text
src/
  app/                    # routing, providers
  shared/                 # ui kit, utils, types transversales
  features/
    invoices/
      api/                # client HTTP / mappers
      model/              # types, schemas
      hooks/              # useInvoices, usePayInvoice
      ui/                 # InvoiceList, InvoiceRow
      index.ts            # API pública del feature
```

- Imports entre features solo vía `index.ts` público.
- Sin dependencias circulares; shared no importa features.

## Checklist rápido

- [ ] ¿La UI puede renderizarse con props mock sin llamar API?
- [ ] ¿Hay estados `loading | empty | error | success`?
- [ ] ¿El tipado cierra el contrato del hook/componente?
- [ ] ¿Hay test del hook o del use case, no solo del markup?

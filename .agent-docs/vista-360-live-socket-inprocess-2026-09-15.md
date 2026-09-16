# Vista 360 live socket — monitor in-process (WAR único)

El deploy staging es **un solo WAR** (`ispadmin-staging.war` con `traffic.jar`). No hay sibling `ispadmin-staging-traffic` en `tomcat-staging`.

`traffic.client-enabled=true` deja el facade STOMP en Core (`CoreTrafficStreamRelay`, JWT técnico). El productor de ticks ya no es un loopback STOMP a sí mismo (`StompTrafficStreamTransport` → `ws://127.0.0.1:8080/ispadmin-staging/ws` fallaba: `SubscriptionTrafficWebSocket` no carga y el handshake interno pide `X-Traffic-Key`).

En el WAR único el bean `SubscriptionTrafficLiveMonitor` implementa `LiveTrafficStreamPort` y pollea `/queue/simple` en proceso. `StompTrafficStreamTransport` solo existe si ese bean no está (core hablando con un WAR traffic aparte).

El browser sigue mandando `{subscriptionId}`; la identidad IP xor PPPoE sale del directorio.

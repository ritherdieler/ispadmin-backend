# wispAdministrator



## Getting started

To make it easy for you to get started with GitLab, here's a list of recommended next steps.

Already a pro? Just edit this README.md and make it your own. Want to make it easy? [Use the template at the bottom](#editing-this-readme)!

## Add your files

- [ ] [Create](https://docs.gitlab.com/ee/user/project/repository/web_editor.html#create-a-file) or [upload](https://docs.gitlab.com/ee/user/project/repository/web_editor.html#upload-a-file) files
- [ ] [Add files using the command line](https://docs.gitlab.com/ee/gitlab-basics/add-file.html#add-a-file-using-the-command-line) or push an existing Git repository with the following command:

```
cd existing_repo
git remote add origin https://gitlab.com/dscorp/wispadministrator.git
git branch -M main
git push -uf origin main
```

## Integrate with your tools

- [ ] [Set up project integrations](https://gitlab.com/dscorp/wispadministrator/-/settings/integrations)

## Collaborate with your team

- [ ] [Invite team members and collaborators](https://docs.gitlab.com/ee/user/project/members/)
- [ ] [Create a new merge request](https://docs.gitlab.com/ee/user/project/merge_requests/creating_merge_requests.html)
- [ ] [Automatically close issues from merge requests](https://docs.gitlab.com/ee/user/project/issues/managing_issues.html#closing-issues-automatically)
- [ ] [Enable merge request approvals](https://docs.gitlab.com/ee/user/project/merge_requests/approvals/)
- [ ] [Automatically merge when pipeline succeeds](https://docs.gitlab.com/ee/user/project/merge_requests/merge_when_pipeline_succeeds.html)

## Test and Deploy

Use the built-in continuous integration in GitLab.

- [ ] [Get started with GitLab CI/CD](https://docs.gitlab.com/ee/ci/quick_start/index.html)
- [ ] [Analyze your code for known vulnerabilities with Static Application Security Testing(SAST)](https://docs.gitlab.com/ee/user/application_security/sast/)
- [ ] [Deploy to Kubernetes, Amazon EC2, or Amazon ECS using Auto Deploy](https://docs.gitlab.com/ee/topics/autodevops/requirements.html)
- [ ] [Use pull-based deployments for improved Kubernetes management](https://docs.gitlab.com/ee/user/clusters/agent/)
- [ ] [Set up protected environments](https://docs.gitlab.com/ee/ci/environments/protected_environments.html)

***

# Editing this README

When you're ready to make this README your own, just edit this file and use the handy template below (or feel free to structure it however you want - this is just a starting point!). Thank you to [makeareadme.com](https://www.makeareadme.com/) for this template.

## Suggestions for a good README
Every project is different, so consider which of these sections apply to yours. The sections used in the template are suggestions for most open source projects. Also keep in mind that while a README can be too long and detailed, too long is better than too short. If you think your README is too long, consider utilizing another form of documentation rather than cutting out information.

## Name
Choose a self-explaining name for your project.

## Description
Let people know what your project can do specifically. Provide context and add a link to any reference visitors might be unfamiliar with. A list of Features or a Background subsection can also be added here. If there are alternatives to your project, this is a good place to list differentiating factors.

## Badges
On some READMEs, you may see small images that convey metadata, such as whether or not all the tests are passing for the project. You can use Shields to add some to your README. Many services also have instructions for adding a badge.

## Visuals
Depending on what you are making, it can be a good idea to include screenshots or even a video (you'll frequently see GIFs rather than actual videos). Tools like ttygif can help, but check out Asciinema for a more sophisticated method.

## Installation
Within a particular ecosystem, there may be a common way of installing things, such as using Yarn, NuGet, or Homebrew. However, consider the possibility that whoever is reading your README is a novice and would like more guidance. Listing specific steps helps remove ambiguity and gets people to using your project as quickly as possible. If it only runs in a specific context like a particular programming language version or operating system or has dependencies that have to be installed manually, also add a Requirements subsection.

## Usage
Use examples liberally, and show the expected output if you can. It's helpful to have inline the smallest example of usage that you can demonstrate, while providing links to more sophisticated examples if they are too long to reasonably include in the README.

## Support
Tell people where they can go to for help. It can be any combination of an issue tracker, a chat room, an email address, etc.

## Roadmap
If you have ideas for releases in the future, it is a good idea to list them in the README.

## Contributing
State if you are open to contributions and what your requirements are for accepting them.

For people who want to make changes to your project, it's helpful to have some documentation on how to get started. Perhaps there is a script that they should run or some environment variables that they need to set. Make these steps explicit. These instructions could also be useful to your future self.

You can also document commands to lint the code or run tests. These steps help to ensure high code quality and reduce the likelihood that the changes inadvertently break something. Having instructions for running tests is especially helpful if it requires external setup, such as starting a Selenium server for testing in a browser.

## Authors and acknowledgment
Show your appreciation to those who have contributed to the project.

## License
For open source projects, say how it is licensed.

## Project status
If you have run out of energy or time for your project, put a note at the top of the README saying that development has slowed down or stopped completely. Someone may choose to fork your project or volunteer to step in as a maintainer or owner, allowing your project to keep going. You can also make an explicit request for maintainers.

# WispAdmin Backend

## Sistema de Monitoreo de Tráfico en Tiem Real

### Arquitectura de Conexiones Persistentes

El sistema implementa un mecanismo robusto para monitorear el tráfico de interfaces de dispositivos MikroTik en tiempo real, optimizando el uso de recursos y previniendo fugas de memoria.

#### Componentes Principales

1. **InterfaceTrafficWebSocket**: Controlador WebSocket que gestiona sesiones y conexiones
2. **WebSocketEventListener**: Listener que detecta desconexiones de usuarios
3. **MikroTikConnectionService**: Servicio que maneja conexiones persistentes a dispositivos

#### Comportamiento del Sistema

- **Gestión de Sesiones**: Cada pestaña/usuario tiene una sesión WebSocket única
- **Conexiones Compartidas**: Múltiples sesiones pueden monitorear el mismo dispositivo usando una sola conexión MikroTik
- **Limpieza Automática**: Las conexiones se cierran automáticamente cuando no hay sesiones activas
- **Timeout de Inactividad**: Sesiones inactivas se limpian después de 5 minutos

#### Escenarios de Uso

1. **Usuario abre una pestaña**: Se crea una sesión y se inicia monitoreo si es la primera
2. **Usuario abre múltiples pestañas**: Cada pestaña suma una sesión al mismo dispositivo
3. **Usuario cierra una pestaña**: Se elimina la sesión, si no quedan sesiones se cierra la conexión
4. **Cierre abrupto del navegador**: El sistema detecta la desconexión y limpia recursos
5. **Pérdida de conexión**: Timeout automático limpia sesiones inactivas

#### Endpoints Disponibles

- `GET /networkDevice/connection-stats`: Estadísticas de conexiones activas
- `POST /app/traffic/start`: Iniciar monitoreo de tráfico
- `POST /app/traffic/stop`: Detener monitoreo de tráfico

#### Ventajas de la Implementación

- ✅ **Eficiencia**: Una conexión MikroTik por dispositivo, sin importar cuántas pestañas
- ✅ **Escalabilidad**: Manejo inteligente de múltiples usuarios simultáneos
- ✅ **Robustez**: Limpieza automática previene fugas de recursos
- ✅ **Monitoreo**: Endpoint de estadísticas para supervisión del sistema

#### Para Futuras Implementaciones

Al implementar sistemas similares de monitoreo en tiempo real:

1. **Siempre usar tracking de sesiones** para evitar conexiones duplicadas
2. **Implementar listeners de desconexión** para limpieza automática
3. **Configurar timeouts** para sesiones inactivas
4. **Proporcionar endpoints de monitoreo** para supervisión del sistema
5. **Documentar el comportamiento** para facilitar mantenimiento

#### Configuración

- **Intervalo de actualización**: 1 segundo
- **Timeout de sesión inactiva**: 5 minutos
- **Limpieza automática**: Cada minuto
- **Pool de threads**: 10 threads para tareas programadas

---

## Instalación y Configuración

### Requisitos Previos

- Java 17 o superior
- Kotlin 1.8+
- Maven 3.6+
- Base de datos MySQL/PostgreSQL

### Configuración de la Base de Datos

1. Crear la base de datos
2. Configurar las credenciales en `application.properties`
3. Ejecutar las migraciones

### Variables de Entorno

```properties
# Base de datos
spring.datasource.url=jdbc:mysql://localhost:3306/wispadmin
spring.datasource.username=your_username
spring.datasource.password=your_password

# WebSocket
spring.websocket.max-text-message-size=8192
spring.websocket.max-binary-message-size=8192

# MikroTik
mikrotik.connection.timeout=10000
mikrotik.connection.retry-attempts=3
```

### Ejecución

```bash
# Compilar el proyecto
./mvnw clean compile

# Ejecutar en modo desarrollo
./mvnw spring-boot:run

# Ejecutar tests
./mvnw test
```

## Estructura del Proyecto

```
src/main/kotlin/com/dscorp/wispadmin/
├── controller/          # Controladores REST
├── service/            # Lógica de negocio
├── repository/         # Acceso a datos
├── websocket/          # WebSockets y eventos
├── config/             # Configuraciones
└── util/               # Utilidades
```

## API Endpoints

### Network Devices
- `GET /networkDevice` - Listar todos los dispositivos
- `GET /networkDevice/coreTypes` - Dispositivos Cloud Core Router
- `GET /networkDevice/connection-stats` - Estadísticas de conexiones

### WebSocket
- `/topic/traffic/{deviceId}` - Stream de datos de tráfico
- `/app/traffic/start` - Iniciar monitoreo
- `/app/traffic/stop` - Detener monitoreo

## Documentación relacionada

- `.agent-docs/arquitectura-red-dual-mikrotik.md` - Arquitectura de red dual Mikrotik (VLAN 1 / VLAN 100, MK1/MK2, integración ispAdmin)
- `.agent-docs/checklist-mk2-vlan1-sfp-sfpplus3.md` - Checklist cutover VLAN 1 en MK2 puerto `sfp-sfpplus3`
- `MIKROTIK_MOCK_README.md` - Configuración de mock/real para MikroTik en desarrollo
- `OLT_MOCK_README.md` - Configuración de mock para OLT

## Contribución

1. Fork el proyecto
2. Crear una rama para tu feature
3. Commit tus cambios
4. Push a la rama
5. Crear un Pull Request

## Licencia

Este proyecto está bajo la licencia MIT.

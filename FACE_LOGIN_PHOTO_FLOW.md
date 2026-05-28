# Login facial por foto

## Objetivo

La app Android no genera descriptores faciales ni ejecuta JavaScript. Android solo abre la camara con CameraX, toma una foto comprimida y la envia al backend. El backend genera el descriptor compatible con los registros existentes de `face_data.face_embedding`, compara y devuelve el usuario si hay coincidencia.

## Flujo

1. Android abre `FaceLoginScreen`.
2. `FaceLoginScreen` usa CameraX para mostrar camara frontal.
3. CameraX captura una foto.
4. Android redimensiona la foto a un maximo de 720px y la comprime como JPEG al 80%.
5. `LoginViewModel.doFaceLogin(photo)` llama al repositorio.
6. `Repository.loginWithFace(photo)` envia multipart `photo` a `POST /users/login/face/photo`.
7. `UserController.loginWithFacePhoto` recibe la foto.
8. `FaceVerifyService.identifyUserFromPhotoForLogin` pide el descriptor al motor facial.
9. `FacePhotoDescriptorService` ejecuta `face-engine/generate-descriptor.cjs`.
10. El script abre Chrome headless, carga `face-api.min.js` y modelos, genera descriptor de 128 valores.
11. `FaceVerifyService` compara el descriptor contra `face_data.face_embedding`.
12. Si hay coincidencia, el backend devuelve `UserDto`; Android guarda sesion e ingresa.

## Archivos Android

- `FaceLoginScreen.kt`: camara, captura, compresion y envio de foto.
- `LoginViewModel.kt`: dispara el login facial y maneja estados.
- `RestApiServices.kt`: declara `POST users/login/face/photo`.
- `Repository.kt`: arma el multipart y guarda sesion si la respuesta es correcta.
- `AuthNavGraph.kt`: navega a la pantalla facial y entra al sistema en `LoginSuccess`.

## Archivos Backend

- `UserController.kt`: endpoint `POST /users/login/face/photo`.
- `FaceVerifyService.kt`: reutiliza la comparacion contra `face_data`.
- `FacePhotoDescriptorService.kt`: convierte foto en descriptor facial.
- `face-engine/generate-descriptor.cjs`: motor de descriptor basado en Chrome headless.
- `application-dev.properties`: rutas locales para modelos, Chrome y limite multipart.

## Por que se usa Chrome headless en backend

Los datos actuales de `face_data.face_embedding` fueron generados con `face-api.js`. Para no re-registrar rostros, el backend debe generar descriptores compatibles con ese modelo. Android ya no usa JavaScript; el backend mantiene esa compatibilidad hasta que se migre a otro modelo nativo y se vuelvan a registrar embeddings.

## Requisitos locales

- Backend corriendo en puerto 8080.
- Chrome instalado en `C:/Program Files/Google/Chrome/Application/chrome.exe`.
- Modelos en `C:/Users/EDWIN/OneDrive/Desktop/Asistencia_giga/asistencia-frontend/public/models`.
- `face-api.min.js` en `C:/Users/EDWIN/OneDrive/Desktop/Asistencia_giga/asistencia-frontend/node_modules/face-api.js/dist/face-api.min.js`.
- Para celular fisico local: `adb reverse tcp:8080 tcp:8080`.

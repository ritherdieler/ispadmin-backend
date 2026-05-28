# 🔧 Configuración de Profiles - Backend

## 📋 Resumen

El backend está configurado para usar diferentes ambientes (profiles) de Spring Boot para desarrollo y producción, cada uno con su propio proyecto de Firebase.

## 🚀 Profiles Disponibles

### **Desarrollo (`dev`)**
- **Base de datos**: `ispadmin_dev` (localhost)
- **Firebase**: `ispadmin-dev`
- **Pagos**: Configuración de test
- **Logging**: Completo para debugging
- **SSL**: Deshabilitado

### **Producción (`prod`)**
- **Base de datos**: `ispadmin` (servidor remoto)
- **Firebase**: `ispadmin-687ca`
- **Pagos**: Configuración de producción
- **Logging**: Mínimo para performance
- **SSL**: Habilitado

## 🎯 Cómo Ejecutar

### **Opción 1: Scripts Automatizados**
```bash
# Desarrollo
./run-dev.sh

# Producción
./run-prod.sh
```

### **Opción 2: Maven Directo**
```bash
# Desarrollo
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Producción
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### **Opción 3: IntelliJ IDEA**

#### **Configuración por Defecto**
- Por defecto se ejecuta con profile `dev`
- Configurado en `application.properties`: `spring.profiles.active=dev`

#### **Cambiar Profile en IntelliJ**
1. **Run** → **Edit Configurations**
2. Seleccionar tu configuración de Spring Boot
3. En **Program arguments** agregar:
   ```
   --spring.profiles.active=prod
   ```
4. O en **VM options**:
   ```
   -Dspring.profiles.active=prod
   ```

## 📁 Archivos de Configuración

```
src/main/resources/
├── application.properties          # Configuración base
├── application-dev.properties      # Configuración desarrollo
├── application-prod.properties     # Configuración producción
├── firebase_service_account_dev.json   # Firebase desarrollo
└── firebase_service_account_prod.json  # Firebase producción
```

## 🔥 Firebase

### **Desarrollo**
- Proyecto: `ispadmin-dev`
- Service Account: `firebase_service_account_dev.json`
- Configurado en: `application-dev.properties`

### **Producción**
- Proyecto: `ispadmin-687ca`
- Service Account: `firebase_service_account_prod.json`
- Configurado en: `application-prod.properties`

## ✅ Verificación

### **Logs de Inicio**
Cuando inicies la aplicación, verás en los logs:
```
The following profiles are active: dev
```

### **Variables de Entorno**
Puedes verificar qué configuración está activa revisando:
- Base de datos conectada
- Firebase project ID en los logs
- Configuración de pagos activa

## 🚨 Notas Importantes

1. **No definir `spring.profiles.active`** en archivos específicos de profile
2. **Solo en `application.properties`** se define el profile por defecto
3. **Los archivos de Firebase** deben ser reemplazados con los reales
4. **Base de datos de desarrollo** debe existir: `ispadmin_dev`

## 🔄 Flujo de Trabajo Recomendado

1. **Desarrollo diario**: Usar profile `dev` (por defecto)
2. **Testing**: Cambiar a `prod` para probar configuración de producción
3. **Deploy**: Usar `prod` en servidor de producción

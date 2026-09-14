#!/bin/bash

# Script para eliminar anotaciones @CrossOrigin redundantes de los controladores
# La configuración global en CorsConfig.kt es suficiente

CONTROLLERS=(
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/OutLayController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NetworkDeviceConnectionController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MockOltDebugController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AssistanceTicketController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PlanController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PlaceController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NapBoxController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NetworkDeviceController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/UserController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AppManagementController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FixedCostController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/IpPoolController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/DashBoardController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/InstallationOrderController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/VerifyResultResource.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/HealthResource.java"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/CreateResource.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/TechnicianController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/ReportController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/OnuController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MufaController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MainController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/LogViewerController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FcmController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CouponController.kt"
    "core/src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AppVersionController.kt"
)

for controller in "${CONTROLLERS[@]}"; do
    if [ -f "$controller" ]; then
        echo "Procesando: $controller"
        
        # Verificar si tiene @CrossOrigin
        if grep -q "@CrossOrigin" "$controller"; then
            echo "  Eliminando @CrossOrigin redundante de $controller"
            
            # Crear archivo temporal
            temp_file=$(mktemp)
            
            # Eliminar líneas que contengan @CrossOrigin
            grep -v "@CrossOrigin" "$controller" > "$temp_file"
            
            # Reemplazar el archivo original
            mv "$temp_file" "$controller"
            echo "  ✅ Completado"
        else
            echo "  ⚠️  No tiene @CrossOrigin"
        fi
    else
        echo "❌ Archivo no encontrado: $controller"
    fi
done

echo "🎉 Proceso completado!"
echo "💡 La configuración global en CorsConfig.kt es suficiente para todos los controladores."





#!/bin/bash

# Script para eliminar anotaciones @CrossOrigin redundantes de los controladores
# La configuración global en CorsConfig.kt es suficiente

CONTROLLERS=(
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/OutLayController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NetworkDeviceConnectionController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MockOltDebugController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AssistanceTicketController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PlanController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/PlaceController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NapBoxController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/NetworkDeviceController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/UserController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AppManagementController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FixedCostController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/IpPoolController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/DashBoardController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/InstallationOrderController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/VerifyResultResource.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/HealthResource.java"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/izipay/CreateResource.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/TechnicianController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/ReportController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/OnuController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MufaController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/MainController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/LogViewerController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/FcmController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/CouponController.kt"
    "src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/AppVersionController.kt"
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





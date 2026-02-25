# BebeCam 👶📷

BebeCam es un sistema de monitoreo para bebés diseñado específicamente para su uso en automóviles. Utiliza una **Radxa Zero 3W** para capturar video en tiempo real de la silla trasera (a contramarcha) y transmitirlo de forma segura a un smartphone o tableta a través de una red Wi-Fi dedicada.

## Características Principales

- **Baja Latencia**: Transmisión WebRTC optimizada para ver al bebé en tiempo real.
- **Hardware Potente**: Uso del procesador Rockchip RK3566 para encoding por hardware (H.264).
- **Red Autónoma**: La placa actúa como su propio punto de acceso Wi-Fi (BebeCam AP).
- **Resiliencia**: Hardware Watchdog activado y reinicio automático de servicios en caso de fallos.
- **Protección de Tarjeta SD**: Configurado para escribir logs en RAM y minimizar el desgaste de la tarjeta SD.
- **Dashboard Web**: Interfaz simple en Go para ver el flujo de video directamente en el navegador.

## Lista de Compras (Hardware Actualizado) 🛒

Para este proyecto estamos utilizando:

1.  **SBC**: **Radxa Zero 3W** (Rockchip RK3566). Ofrece excelente rendimiento para streaming gracias a su VPU.
2.  **Módulo de Cámara**: **Raspberry Pi Camera Module 3 NoIR Wide**.
    - **NoIR**: Fundamental para ver de noche (sin filtro infrarrojo).
    - **Wide**: Lente gran angular (120°) para capturar todo el asiento trasero a corta distancia.
3.  **Iluminador Infrarrojo**: Un pequeño panel LED IR para que la cámara NoIR pueda "ver" en la total oscuridad del coche.
4.  **Cable de Cámara**: Cable de 22 pines (paso 0.5mm) para Radxa/Zero.
5.  **Tarjeta MicroSD**: **16GB o 32GB Class 10 / High Endurance**.
6.  **Fuente de Alimentación para Coche**: Adaptador de 12V con protección de bajo voltaje (Hardwire Kit recomendado).
7.  **Montaje/Soporte**: Soporte para la cámara compatible con el reposacabezas.

## Instalación

El despliegue se maneja íntegramente a través de **Ansible**.

### Prerrequisitos

- Un ordenador con Ansible instalado.
- Acceso SSH a la Radxa Zero 3W (con Radxa OS o Debian).
- **Importante**: Antes de correr Ansible, debes habilitar el overlay de la cámara.
    - Ejecuta `sudo rsetup` en la placa.
    - Navega a `Overlays` -> `Manage overlays`.
    - Busca y habilita el overlay correspondiente a tu cámara (ej: `cam-imx219` o el que corresponda a la Camera 3 si está disponible).
    - Reinicia la placa.

### Despliegue

1.  Clona este repositorio.
2.  Edita el archivo `ansible/inventory.ini` con la IP o hostname de tu Raspberry Pi.
3.  Ejecuta el playbook:

```bash
cd ansible
ansible-playbook -i inventory.ini playbook.yml
```

## Arquitectura

- **MediaMTX**: El motor detrás del streaming, configurado para capturar video mediante `libcamera-vid` y servirlo por WebRTC.
- **Ansible Roles**:
    - `ap`: Configura el punto de acceso Wi-Fi usando NetworkManager.
    - `mediamtx`: Instala y configura el servidor de streaming.
    - `hardening`: Activa medidas de seguridad y resiliencia del sistema.
    - `dashboard`: Despliega una pequeña aplicación web en Go para visualizar el stream.

## Roadmap 🚀

Próximas mejoras planeadas para aprovechar al máximo la Radxa Zero 3W:

- [ ] **Detección de Sueño**: Uso del NPU para identificar si el bebé tiene los ojos abiertos o cerrados.
- [ ] **Alerta de Obstrucción**: Detección inteligente si algo tapa la cara del bebé o la cámara.

## Licencia

Este proyecto está bajo la licencia MIT.

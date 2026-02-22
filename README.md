# BebeCam 👶📷

BebeCam es un sistema de monitoreo para bebés diseñado específicamente para su uso en automóviles. Utiliza una Raspberry Pi para capturar video en tiempo real de la silla trasera (a contramarcha) y transmitirlo de forma segura a un smartphone o tableta a través de una red Wi-Fi dedicada.

## Características Principales

- **Baja Latencia**: Transmisión WebRTC optimizada para ver al bebé en tiempo real.
- **Red Autónoma**: La Raspberry Pi actúa como su propio punto de acceso Wi-Fi (BebeCam AP).
- **Resiliencia**: Hardware Watchdog activado y reinicio automático de servicios en caso de fallos.
- **Protección de Tarjeta SD**: Configurado para escribir logs en RAM y minimizar el desgaste de la tarjeta SD.
- **Dashboard Web**: Interfaz simple en Go para ver el flujo de video directamente en el navegador.

## Lista de Compras (Hardware Recomendado) 🛒

Para replicar este proyecto, necesitarás:

1.  **Raspberry Pi**: 
    - **Recomendada**: **Raspberry Pi 4 Model B (2GB o más)**. Proporciona la mejor experiencia y fluidez.
    - **Compacta**: **Raspberry Pi Zero 2 W**. "Se la banca" perfectamente para streaming en 720p/1080p gracias a su codificador por hardware, pero puede calentar un poco; asegúrate de usar un pequeño disipador.
2.  **Módulo de Cámara**: 
    - **Raspberry Pi Camera Module 3 NoIR** (Versión **NoIR** es fundamental). Al no tener filtro infrarrojo, permite ver al bebé de noche utilizando un iluminador LED infrarrojo (invisible para el bebé pero visible para la cámara).
    - *Nota*: La versión NoIR hará que los colores diurnos se vean algo "rosados/lavados", pero es el compromiso necesario para el monitoreo nocturno.
3.  **Iluminador Infrarrojo**: Un pequeño panel LED IR de 12V o 5V para que la cámara NoIR pueda "ver" en la total oscuridad del coche.
4.  **Cable de Cámara**: Cable plano para Raspberry Pi (asegúrate de que sea el largo adecuado y compatible con el conector de la Zero 2 W si eliges esa placa, ya que es más pequeño).
5.  **Tarjeta MicroSD**: **16GB o 32GB Class 10 / High Endurance**.
6.  **Fuente de Alimentación para Coche**: 
    - **Importante**: Busca un adaptador que se conecte a una toma de **12V "switched"** (que se apague al quitar el contacto del auto). 
    - **Protección de Batería**: Si tu auto mantiene la toma de 12V siempre encendida, te recomiendo usar un **Hardwire Kit** (como los de las dashcams) que tenga **protección de bajo voltaje**. Esto cortará la energía si la batería del auto baja de cierto nivel (ej. 11.6V) para evitar que te quedes sin poder arrancar el auto.
7.  **Montaje/Soporte**: Soporte para la cámara compatible con el reposacabezas.

## Instalación

El despliegue se maneja íntegramente a través de **Ansible**, lo que garantiza una configuración reproducible y limpia.

### Prerrequisitos

- Un ordenador con Ansible instalado.
- Acceso SSH a la Raspberry Pi (con Raspberry Pi OS Lite).

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

## Licencia

Este proyecto está bajo la licencia MIT.

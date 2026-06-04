# ⚽ PaseDeGol - App de Ticketing Deportivo

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" />
  <img src="https://img.shields.io/badge/Stripe-626CD9?style=for-the-badge&logo=Stripe&logoColor=white" />
</p>

**PaseDeGol** es una aplicación móvil nativa para Android diseñada para revolucionar la gestión y venta de entradas de eventos deportivos (B2B2C). Desarrollada como Trabajo de Fin de Grado (TFG) del ciclo de Desarrollo de Aplicaciones Multiplataforma (DAM), obteniendo una **calificación de 9/10**.

El proyecto simula un entorno profesional de comercio electrónico completo: desde la visualización del catálogo y la gestión del inventario en tiempo real, hasta la validación de pagos segura y la generación de comprobantes digitales (Smart Tickets) mediante códigos QR.

📑 **Nota:** Este proyecto ha sido desarrollado como Trabajo de Fin de Grado de DAM, si quieres ver todo lo realizado (Memoria, Diagramas...) puedes visitar: [https://github.com/javiescobar-dev/proyectoDAM](https://github.com/javiescobar-dev/proyectoDAM)

---

## ✨ Características Principales

La aplicación implementa un sistema de control de acceso basado en roles (RBAC) que adapta la interfaz y las funcionalidades según el tipo de usuario:

### 👤 1. Cliente (Usuario Registrado)
* **Autenticación Segura:** Registro e inicio de sesión mediante Email/Contraseña o **Google Sign-In**.
* **Carrito Persistente:** Estado de compra sincronizado en la nube; el usuario no pierde su selección al cambiar de dispositivo.
* **Pasarela de Pago (Stripe):** Procesamiento de pagos mediante tokenización segura, cumpliendo con estándares PCI (entorno de pruebas).
* **Cartera Digital:** Sección "Mis Entradas" con generación dinámica de códigos QR legibles para el control de accesos.
* **Perfil de Usuario:** Gestión de datos personales y preferencias de notificaciones.

### 🛡️ 2. Administrador
* **Panel de Control (Dashboard):** Herramientas exclusivas accesibles desde la propia app.
* **Gestión de Equipos (CRUD):** Creación, edición, borrado y subida de escudos oficiales a la nube.
* **Programación de Partidos:** Creación de eventos deportivos gestionando aforos (stock) y precios.
* **Control de Concurrencia:** Protección contra sobreventa de entradas mediante transacciones atómicas.

### 🕵️ 3. Usuario Anónimo
* Acceso directo al catálogo de eventos sin fricción inicial para maximizar la retención y conversión.

---

## 🛠️ Stack Tecnológico y Arquitectura

El proyecto está construido siguiendo las directrices de **Material Design** y utiliza el patrón de arquitectura **MVVM (Model-View-ViewModel)** para separar la lógica de negocio de la interfaz de usuario.

* **Lenguaje:** Kotlin
* **Arquitectura:** MVVM + Corrutinas (Programación asíncrona)
* **Base de Datos:** Firebase Cloud Firestore (NoSQL en tiempo real)
* **Autenticación:** Firebase Authentication
* **Almacenamiento Multimedia:** Firebase Storage
* **Backend (Node.js):** Firebase Cloud Functions (Intermediario seguro para la API de Stripe)
* **Notificaciones:** Firebase Cloud Messaging (FCM)
* **Monitorización:** Firebase Crashlytics
* **Librerías de Terceros:**
  * `Glide` (Caché y carga eficiente de imágenes)
  * `Stripe Android SDK` (Procesamiento de pagos)
  * `Lottie` (Animaciones fluidas en formato JSON)
  * `QRGen / ZXing` (Generación de Smart Tickets QR)

---

## 📱 Capturas de Pantalla

*(Reemplaza estos enlaces con las rutas de tus propias imágenes en la carpeta `/screenshots` del repositorio)*

<p align="center">
  <img src="ruta_a_tu_imagen/login.png" width="200" alt="Login">
  <img src="ruta_a_tu_imagen/home.png" width="200" alt="Catálogo">
  <img src="ruta_a_tu_imagen/cart.png" width="200" alt="Carrito">
  <img src="ruta_a_tu_imagen/qr_ticket.png" width="200" alt="Entrada QR">
</p>

---

## 🚀 Instalación y Configuración

Si deseas clonar y probar este proyecto en tu entorno local, sigue estos pasos:

1. **Clonar el repositorio:**
```bash
   git clone https://github.com/javiescobar-dev/PaseDeGol.git
```

2. **Configurar Firebase:**

* Crea un proyecto en Firebase Console.
* Habilita Authentication, Firestore y Storage.
* Descarga el archivo google-services.json y colócalo en el directorio app/ del proyecto.

3. **Configurar Stripe y Cloud Functions:**

* Crea una cuenta de desarrollador en Stripe para obtener tus claves de prueba.
* Despliega la carpeta functions/ en tu entorno de Firebase utilizando Node.js e inserta tu clave secreta de Stripe en las variables de entorno de Firebase.
* Añade tu clave pública de Stripe en el archivo local.properties o en la clase de configuración correspondiente en Android.

4. **Compilar:** Abre el proyecto en Android Studio y sincroniza los archivos de Gradle.

## 👨‍💻 Autor

* Javi Escobar Fernández - Desarrollador Android - [LinkedIn](https://www.linkedin.com/in/javi-escobar-dev) | [GitHub](https://github.com/javiescobar-dev)

Si este proyecto te ha resultado interesante o útil, no dudes en darle una ⭐. ¡Gracias por visitarlo!

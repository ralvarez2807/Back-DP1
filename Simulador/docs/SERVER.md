# Arquitectura de Despliegue

## Tabla de Contenidos

1. [Topología](#1-topología)
2. [Configuración de Nginx](#2-configuración-de-nginx)
3. [Gestión de Procesos](#3-gestión-de-procesos)
4. [Seguridad](#4-seguridad)
5. [Pipeline CI/CD](#5-pipeline-cicd)

---

## 1. Topología

### 1.1 Diagrama de nodos

```
Internet / Cliente
│
└── Nginx — Reverse Proxy (puerto 80, público)
    │
    ├── /          → Archivos estáticos React/Vite  (sin proceso Node)
    └── /api/**    → Spring Boot JAR (puerto 8080, solo interno)
                     └── WebSocket en /api/v1/simulations/*/ws
```

Nginx es el único punto de entrada. Los puertos 8080, 5432 y 6379 **no son accesibles desde el exterior**.

### 1.2 Especificaciones del entorno

| Componente | Detalle |
|------------|---------|
| Proveedor | AWS Academy — EC2 t3.medium |
| SO | Ubuntu 24.04 LTS (x86_64) |
| RAM / CPU / Disco | 4 GB / 2 vCPU / 20 GB SSD (EBS gp3) |
| Proxy | Nginx 1.24+ |
| Frontend | React + Vite — build estático servido por Nginx |
| Backend | Spring Boot 3.x — fat JAR gestionado por systemd |
| Java | OpenJDK 21 LTS |
| BD | PostgreSQL 16 — fase futura |
| Caché | Redis 7 — fase futura |

> **HTTPS:** AWS Academy no proporciona dominio propio, por lo que no se configura TLS en esta etapa. En un entorno con dominio real, añadir `certbot --nginx` y redirigir 80 → 443.

---

## 2. Configuración de Nginx

### 2.1 Routing

| Path | Destino |
|------|---------|
| `/` | Build estático React en `/var/www/tasf/frontend` |
| `/api/**` | Spring Boot en `localhost:8080` (REST + WebSocket) |
| `/health` | Healthcheck propio de Nginx |

El WebSocket vive en `/api/v1/simulations/{id}/ws` — cae bajo `/api/` y hereda sus headers de upgrade. No se necesita un bloque `/ws/` separado.

### 2.2 Configuración técnica

```nginx
# Soporte WebSocket: si es upgrade → "upgrade"; si es HTTP normal → "close"
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80;
    server_name <IP-o-dominio>;
    server_tokens off;

    # Headers de seguridad
    add_header X-Frame-Options        "DENY"        always;
    add_header X-Content-Type-Options "nosniff"     always;
    add_header Referrer-Policy        "no-referrer" always;

    # Frontend — React/Vite build estático
    location / {
        root      /var/www/tasf/frontend;
        try_files $uri $uri/ /index.html;
    }

    # Backend — Spring Boot (REST + WebSocket en /api/v1/simulations/*/ws)
    location /api/ {
        proxy_pass         http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host            $host;
        proxy_set_header   X-Real-IP       $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        # WebSocket
        proxy_set_header   Upgrade         $http_upgrade;
        proxy_set_header   Connection      $connection_upgrade;
        proxy_read_timeout 3600s;
    }

    # Healthcheck
    location /health {
        return 200 "ok\n";
        add_header Content-Type text/plain;
    }
}
```

---

## 3. Gestión de Procesos

### 3.1 Backend — systemd

El backend corre como servicio systemd. Arranca automáticamente con la VM y se reinicia si cae.

Archivo: `/etc/systemd/system/tasf-backend.service`

```ini
[Unit]
Description=TASF B2B Simulator Backend
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/tasf
EnvironmentFile=/etc/tasf/secrets.env
ExecStart=/usr/bin/java -jar /opt/tasf/tasf-backend.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Los logs se consultan con `sudo journalctl -u tasf-backend -f`.

### 3.2 Frontend — sin proceso

`npm run build` genera archivos estáticos en `dist/`. Se copian a `/var/www/tasf/frontend/` y Nginx los sirve directamente. **No hay ningún proceso Node corriendo en producción.**

---

## 4. Seguridad

### 4.1 Firewall — AWS Security Group

| Puerto | Servicio | Acceso |
|--------|----------|--------|
| 22 | SSH | Solo IP del equipo de desarrollo |
| 80 | HTTP | Público (0.0.0.0/0) |
| 8080 | Spring Boot | Cerrado — solo acceso interno |
| 5432 | PostgreSQL | Cerrado — solo acceso local |
| 6379 | Redis | Cerrado — solo acceso local |

UFW en la VM como segunda capa: solo abrir 22 y 80.

### 4.2 Secretos en la VM

Los secretos se almacenan en `/etc/tasf/secrets.env` con permisos `600`. Systemd los inyecta como variables de entorno al proceso. **El archivo nunca está en el repositorio.**

```
JWT_SECRET=<base64 generado con openssl rand -base64 32>
```

### 4.3 Seguridad de aplicación

| Medida | Implementación |
|--------|----------------|
| Autenticación | JWT stateless — secreto desde `EnvironmentFile` |
| Contraseñas | BCrypt |
| Rate limiting | Máx 5 intentos de login por IP/minuto (Bucket4j) |
| Headers HTTP | `X-Frame-Options`, `X-Content-Type-Options` en Nginx y Spring Security |
| CORS | No aplica — front y back comparten dominio vía Nginx |

---

## 5. Pipeline CI/CD

### 5.1 Resumen

| Config | Valor |
|--------|-------|
| Herramienta | GitHub Actions |
| Trigger CI | Pull Request a `main` |
| Trigger CD | Push directo a `main` |
| Destino | VM Ubuntu en AWS Academy vía SSH |

### 5.2 GitHub Secrets requeridos

| Secret | Descripción |
|--------|-------------|
| `SSH_PRIVATE_KEY` | Clave privada SSH para conectarse a la VM |
| `VM_HOST` | IP pública de la VM |
| `VM_USER` | Usuario SSH (`ubuntu`) |
| `JWT_SECRET` | Secreto para firma de tokens JWT |

### 5.3 Fase CI — en cada PR a `main`

1. Checkout del código
2. Configurar OpenJDK 21
3. `mvn -f Simulador/com.tasf.b2b/pom.xml clean package` — si falla, se detiene
4. Ejecución de tests — si alguno falla, se detiene
5. Configurar Node 20
6. `npm ci` en `Front/`
7. `npm run build` — si falla, se detiene

### 5.4 Fase CD — solo en push directo a `main`

1. Conexión SSH a la VM
2. Escribir `JWT_SECRET` en `/etc/tasf/secrets.env`
3. Copiar el JAR a `/opt/tasf/tasf-backend.jar`
4. `sudo systemctl restart tasf-backend`
5. Copiar el build de React (`dist/`) a `/var/www/tasf/frontend/`
6. `curl http://localhost/health` — verificar que responde

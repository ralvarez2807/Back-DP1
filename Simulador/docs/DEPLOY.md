# Manual de Despliegue

Checklist para dejar el servidor operativo desde cero. Seguir en orden.

---

## Requisitos previos (tu máquina local)

- [ ] Tener el archivo `.pem` de la VM de AWS Academy
- [ ] Tener acceso al repositorio en GitHub
- [ ] Maven instalado (`mvn -version`)
- [ ] Node 20 instalado (`node -version`)

---

## Parte 1 — Configuración inicial del servidor (una sola vez)

### 1. Conectarse a la VM

```bash
chmod 400 <clave.pem>
ssh -i <clave.pem> ubuntu@<IP-VM>
```

### 2. Actualizar el sistema

```bash
sudo apt update && sudo apt upgrade -y
```

### 3. Instalar Java 21

```bash
sudo apt install -y openjdk-21-jdk
java -version   # debe mostrar openjdk 21
```

### 4. Instalar Nginx

```bash
sudo apt install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
```

### 5. Configurar el firewall (UFW)

```bash
sudo apt install -y ufw
sudo ufw allow 22
sudo ufw allow 80
sudo ufw enable
sudo ufw status   # debe mostrar 22 y 80 como ALLOW
```

### 6. Crear la estructura de directorios

```bash
sudo mkdir -p /opt/tasf
sudo mkdir -p /var/www/tasf/frontend
sudo mkdir -p /etc/tasf
sudo chown ubuntu:ubuntu /opt/tasf
sudo chown ubuntu:ubuntu /var/www/tasf/frontend
```

### 7. Instalar PostgreSQL y crear la base de datos

```bash
sudo apt install -y postgresql
sudo systemctl enable postgresql
sudo systemctl start postgresql

# Crear usuario y base de datos
sudo -u postgres psql <<'SQL'
CREATE USER tasf WITH PASSWORD 'cambiar_en_produccion';
CREATE DATABASE tasf OWNER tasf;
SQL

# Aplicar el esquema (como superusuario postgres)
psql -U postgres -d tasf -h localhost -f /ruta/local/schema.sql
# (copiar schema.sql a la VM primero con scp, o pegarlo directamente)

# Otorgar permisos al usuario tasf sobre los schemas creados
sudo -u postgres psql -d tasf <<'SQL'
GRANT USAGE ON SCHEMA reference, simulation, live TO tasf;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA reference, simulation, live, public TO tasf;
ALTER DEFAULT PRIVILEGES IN SCHEMA reference, simulation, live GRANT ALL ON TABLES TO tasf;
SQL
```

### 8. Crear el archivo de secretos

```bash
# Generar un JWT_SECRET seguro
JWT=$(openssl rand -base64 32)
sudo tee /etc/tasf/secrets.env > /dev/null <<EOF
JWT_SECRET=$JWT
DB_URL=jdbc:postgresql://localhost:5432/tasf
DB_USER=tasf
DB_PASSWORD=cambiar_en_produccion
EOF
sudo chmod 600 /etc/tasf/secrets.env
sudo chown root:root /etc/tasf/secrets.env

# Mostrar el JWT para copiarlo en GitHub Secrets
echo "Copia este valor en GitHub Secrets → JWT_SECRET: $JWT"
```

### 8. Crear el servicio systemd

```bash
sudo nano /etc/systemd/system/tasf-backend.service
```

Pegar exactamente esto:

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

```bash
sudo systemctl daemon-reload
sudo systemctl enable tasf-backend
```

### 9. Configurar Nginx

```bash
sudo nano /etc/nginx/sites-available/tasf
```

Pegar la configuración de `SERVER.md` sección 2.2, reemplazando `<IP-o-dominio>` con la IP pública de la VM.

```bash
sudo ln -s /etc/nginx/sites-available/tasf /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t           # debe decir "syntax is ok"
sudo systemctl reload nginx
```

---

## Parte 2 — Primer despliegue (desde tu máquina local)

### 10. Compilar y copiar el backend

```bash
# En la raíz del repo
mvn -f Simulador/com.tasf.b2b/pom.xml clean package -DskipTests

scp -i <clave.pem> \
    Simulador/com.tasf.b2b/target/com.tasf.b2b-1.0-SNAPSHOT.jar \
    ubuntu@<IP-VM>:/opt/tasf/tasf-backend.jar
```

### 11. Arrancar el backend

```bash
ssh -i <clave.pem> ubuntu@<IP-VM> "sudo systemctl start tasf-backend"
ssh -i <clave.pem> ubuntu@<IP-VM> "sudo systemctl status tasf-backend"
# Debe aparecer "active (running)"
```

### 12. Compilar y copiar el frontend

```bash
cd Front
npm ci
npm run build

scp -i <clave.pem> -r dist/* ubuntu@<IP-VM>:/var/www/tasf/frontend/
```

---

## Parte 3 — Verificación

### 13. Comprobar que todo responde

```bash
# Healthcheck de Nginx
curl http://<IP-VM>/health
# Respuesta esperada: ok

# Login al API
curl -s http://<IP-VM>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<password>"}' | python3 -m json.tool
# Respuesta esperada: {"accessToken": "...", "expiresAt": "..."}

# Frontend
# Abrir http://<IP-VM> en el navegador — debe cargar la app
```

### 14. Revisar logs si algo falla

```bash
ssh -i <clave.pem> ubuntu@<IP-VM>

# Logs del backend en tiempo real
sudo journalctl -u tasf-backend -f

# Últimas 100 líneas
sudo journalctl -u tasf-backend -n 100

# Logs de Nginx
sudo tail -f /var/log/nginx/error.log
```

---

## Parte 4 — Configurar CI/CD

### 15. Añadir GitHub Secrets

En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**

| Secret | Valor |
|--------|-------|
| `SSH_PRIVATE_KEY` | Contenido completo del archivo `.pem` |
| `VM_HOST` | IP pública de la VM |
| `VM_USER` | `ubuntu` |
| `JWT_SECRET` | El valor generado en el paso 7 |

Desde este punto, cada push a `main` despliega automáticamente.

---

## Actualizaciones manuales (sin CI/CD)

Si necesitas desplegar manualmente sin esperar al pipeline:

```bash
# Backend
mvn -f Simulador/com.tasf.b2b/pom.xml clean package -DskipTests
scp -i <clave.pem> Simulador/com.tasf.b2b/target/com.tasf.b2b-1.0-SNAPSHOT.jar \
    ubuntu@<IP-VM>:/opt/tasf/tasf-backend.jar
ssh -i <clave.pem> ubuntu@<IP-VM> "sudo systemctl restart tasf-backend"

# Frontend
cd Front && npm run build
scp -i <clave.pem> -r dist/* ubuntu@<IP-VM>:/var/www/tasf/frontend/
```

---

## Referencia rápida — Comandos útiles en la VM

```bash
# Estado y control del backend
sudo systemctl status tasf-backend
sudo systemctl restart tasf-backend
sudo systemctl stop tasf-backend

# Logs
sudo journalctl -u tasf-backend -f        # en tiempo real
sudo journalctl -u tasf-backend -n 100    # últimas 100 líneas

# Nginx
sudo nginx -t                              # verificar configuración
sudo systemctl reload nginx               # recargar sin downtime
sudo tail -f /var/log/nginx/access.log

# Ver secretos activos
sudo cat /etc/tasf/secrets.env
```

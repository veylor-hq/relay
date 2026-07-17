# Veylor Relay Production Deployment Guide

This guide provides step-by-step instructions on deploying the Veylor Relay service on an Ubuntu server, utilising Docker, Caddy (for automatic HTTPS), and Tailscale for internal secure networking.

---

## 1. CI/CD: GitHub Actions Setup

To enable manual triggering and automated builds to your registry, add the following secrets to your GitHub repository under **Settings > Secrets and variables > Actions > Repository secrets**:

1. `REGISTRY_USERNAME` - The username for your private Docker registry.
2. `REGISTRY_PASSWORD` - The token / secret

### Triggering the Build
1. Go to the **Actions** tab in your GitHub repository.
2. Select **Build and Push Docker Image** from the list of workflows.
3. Click **Run workflow**, choose your branch (e.g., `main`), select or type your desired tag (defaults to `latest`), and click the run button.

---

## 2. Server Setup (Ubuntu)

### Install Tailscale (Recommended Host-Level setup)
Having Tailscale running directly on the host is the cleanest way to establish secure internal gRPC/REST communications without complex container network routing:

```bash
# Install Tailscale
curl -fsSL https://tailscale.com/install.sh | sh

# Authenticate the node to your tailnet
sudo tailscale up
```

Once connected, find your server's Tailscale private IP address using `tailscale ip -4`. This IP address can be used by other services on your Tailspace to make secure gRPC or REST calls directly to port `8080` (mapped to `127.0.0.1:8080` or bound to the Tailscale interface).

---

## 3. Configuration Setup on Server

Create a deployment directory on your server (e.g. `/opt/relay`) and copy the following files there:
- `docker-compose.prod.yml`
- `Caddyfile`

### Environment File (`.env`)
Create a `.env` file in the same directory to hold secrets and configuration.

---

## 4. Run the Stack

1. **Log in to your private registry** on the server so Docker can pull the image:
   ```bash
   docker login registry.url -u <your-registry-username>
   ```

2. **Start the containers**:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env up -d
   ```

3. **Check status**:
   ```bash
   docker compose -f docker-compose.prod.yml ps
   docker compose -f docker-compose.prod.yml logs -f
   ```

---

## 5. Tailscale & gRPC Routing

Since the `relay` container maps port `8080` to `127.0.0.1:8080`, it is only accessible locally or through a proxy. 

- **For public REST access**: Caddy forwards traffic from `https://relay.yourdomain.com` (port 443) safely to the local container.
- **For secure internal gRPC / REST (Tailscale)**: 
  If you have other services in the same Tailnet that need to connect to Relay without exposing it to the open internet, you can update the ports section in `docker-compose.prod.yml` to bind directly to the Tailscale IP interface:
  ```yaml
  ports:
    - "<your-tailscale-ip>:8080:8080"
  ```
  This keeps all internal gRPC/REST communications strictly isolated within the encrypted Tailscale network!

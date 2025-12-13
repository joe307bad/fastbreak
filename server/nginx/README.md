# Nginx Static File Server

## Build and Run

```bash
docker build -t fastbreak-nginx .

docker run -d \
  --name fastbreak-nginx \
  -p 8080:80 \
  -v /Users/joebad/Source/fastbreak/server/nginx/static:/usr/share/nginx/html \
  fastbreak-nginx
```

Files added to `static/` are immediately available at `http://localhost:8080`

## Stop/Remove

```bash
docker stop fastbreak-nginx && docker rm fastbreak-nginx
```

FROM nginx:alpine

# Copy nginx configuration
COPY conf/nginx.conf /etc/nginx/nginx.conf

# Copy static files
COPY static /usr/share/nginx/html

# Expose port 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

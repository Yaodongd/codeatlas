FROM node:24-alpine AS build
WORKDIR /app
ARG VITE_BASE_PATH=/codeatlas/
ENV VITE_BASE_PATH=$VITE_BASE_PATH
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY deploy/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80


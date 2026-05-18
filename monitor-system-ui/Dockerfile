FROM node:18 AS build
COPY . /app
WORKDIR /app
RUN npm install && npm run build

FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# 复制 nginx 配置，解决 Vue 路由刷新 404 问题
COPY nginx.conf /etc/nginx/conf.d/default.conf 
EXPOSE 80
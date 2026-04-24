FROM docker.io/library/node:22-alpine AS build
RUN npm install -g pnpm
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY webapp/package.json webapp/
RUN pnpm install --frozen-lockfile --filter webapp
COPY webapp/ webapp/
RUN pnpm --filter webapp build

FROM docker.io/library/nginx:alpine
COPY --from=build /app/webapp/dist /usr/share/nginx/html
COPY webapp/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

FROM docker.io/library/node:20-slim AS build
WORKDIR /webapp
COPY package.json ./
RUN npm install --ignore-scripts
COPY . .
RUN npm run build

FROM docker.io/library/nginx:alpine
COPY --from=build /webapp/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

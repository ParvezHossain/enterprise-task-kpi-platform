FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY shared ./shared
COPY task-management-ui ./task-management-ui
COPY kpi-ui ./kpi-ui
COPY style-guide.html build.mjs ./
RUN npm run build

FROM nginx:1.30.5-alpine@sha256:0985e772fb9f729e6fa0980da05fca5d9c468e870eed43071545afa9d2e27d94
RUN addgroup -S -g 10001 platform && adduser -S -D -H -u 10001 -G platform platform
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /build/shared /usr/share/nginx/html/shared
COPY --from=build /build/task-management-ui /usr/share/nginx/html/task-management-ui
COPY --from=build /build/kpi-ui /usr/share/nginx/html/kpi-ui
COPY --from=build /build/style-guide.html /usr/share/nginx/html/style-guide.html
USER 10001:10001
EXPOSE 8080 8081
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1

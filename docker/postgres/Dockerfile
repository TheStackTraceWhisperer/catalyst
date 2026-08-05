FROM postgres:17

ENV POSTGRES_DB=catalyst
ENV POSTGRES_USER=catalyst
ENV POSTGRES_PASSWORD=catalyst

COPY init/001-schema.sql /docker-entrypoint-initdb.d/001-schema.sql

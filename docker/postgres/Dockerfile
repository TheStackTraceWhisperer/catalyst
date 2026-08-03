FROM postgres:17

ENV POSTGRES_DB=ffxi
ENV POSTGRES_USER=ffxi
ENV POSTGRES_PASSWORD=ffxi

COPY init/001-schema.sql /docker-entrypoint-initdb.d/001-schema.sql

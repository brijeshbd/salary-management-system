#!/bin/sh
set -e

# Render (and Heroku-style platforms) inject one Postgres connection string as DATABASE_URL in
# postgres://user:pass@host:port/db form. Spring's spring.datasource.url needs a jdbc: URL and
# the credentials as separate properties, so translate it here rather than requiring a
# platform-specific Spring profile for every host that only provides a single connection string.
if [ -n "$DATABASE_URL" ]; then
  proto="$(printf '%s' "$DATABASE_URL" | sed -E 's#^([a-zA-Z]+)://.*#\1#')"
  rest="$(printf '%s' "$DATABASE_URL" | sed -E "s#^${proto}://##")"
  userpass="$(printf '%s' "$rest" | cut -d '@' -f1)"
  hostdb="$(printf '%s' "$rest" | cut -d '@' -f2)"

  export SPRING_DATASOURCE_USERNAME="$(printf '%s' "$userpass" | cut -d ':' -f1)"
  export SPRING_DATASOURCE_PASSWORD="$(printf '%s' "$userpass" | cut -d ':' -f2-)"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${hostdb}?reWriteBatchedInserts=true"
fi

exec java -jar app.jar

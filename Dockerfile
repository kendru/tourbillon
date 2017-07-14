FROM clojure:lein-alpine

EXPOSE 80
ENV HTTP_PORT="80" APP_ENV="prod"

RUN mkdir -p /srv
ADD target/tourbillon-standalone.jar /srv/tourbillon.jar

CMD ["java", "-jar", "/srv/tourbillon.jar"]

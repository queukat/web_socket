FROM hseeberger/scala-sbt:11.0.11_1.5.5_2.13.6 as build

WORKDIR /app

COPY . /app

RUN sbt clean assembly

FROM openjdk:11-jre-slim

COPY --from=build /app/target/scala-2.13/WebSocketOrderedMessages-assembly-0.1.jar /app/WebSocketOrderedMessages.jar

CMD ["java", "-jar", "/app/WebSocketOrderedMessages.jar"]

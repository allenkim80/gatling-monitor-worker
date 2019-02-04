FROM openjdk:8-jre-alpine

ADD target/scala-**/*.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
# Use Java 21 base image
FROM eclipse-temurin:21-jdk-jammy

# Set working directory
WORKDIR /app

# Copy your Spring Boot jar into the container
COPY ConverterTool-0.0.1-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 8094

# Run the application
CMD java -Xms256m -Xmx6g -Djava.awt.headless=true -Dspring.mvc.task.execution.threadPool.coreSize=50 -Dspring.mvc.task.execution.threadPool.maxSize=400 -Dserver.port=8094 -jar app.jar

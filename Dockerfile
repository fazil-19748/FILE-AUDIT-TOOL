FROM alpine:3.21.3
RUN apk update
RUN apk add openjdk21
RUN apk add tzdata
RUN apk add ghostscript
RUN rm -rf /var/cache/apk/*
ENV TZ=Asia/Kolkata
# Set working directory
WORKDIR /app

# Copy your Spring Boot jar into the container
COPY ConverterTool-0.0.1-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 8094

# Run the application
CMD java -Xms256m -Xmx6g -Djava.awt.headless=true -Dspring.mvc.task.execution.threadPool.coreSize=50 -Dspring.mvc.task.execution.threadPool.maxSize=400 -Dserver.port=8094 -jar app.jar

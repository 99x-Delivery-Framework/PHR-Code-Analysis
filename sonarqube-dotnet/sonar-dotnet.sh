#!/bin/bash

# Modify variables
PROJECT_NAME="csharp-test"
PROJECT_DIR="D:\roguesharprlnetsamples"

# For example
# PROJECT_NAME="sonartest"
# PROJECT_DIR="D:\projects\sonar-test-project"

SONARQUBE_VERSION="9.9.5-community"
SONARQUBE_PORT="9000"

# Pull SonarQube Docker image
echo "Pulling SonarQube Docker image..."
docker pull sonarqube:${SONARQUBE_VERSION}

# Run SonarQube container
echo "Starting SonarQube container..."
docker run -d --name sonarqube -p ${SONARQUBE_PORT}:9000 -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true sonarqube:${SONARQUBE_VERSION}

# Wait for SonarQube to start
echo "Waiting for SonarQube to start..."
while ! curl -s http://localhost:${SONARQUBE_PORT} | grep -q "SonarQube"; do
  echo "Waiting for SonarQube to be available..."
  sleep 10
done
echo "SonarQube is running!"

sleep 30

container_ip=$( { docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "sonarqube"; } 2>&1 )

# Create sonar-project.properties file in the project directory
echo "Creating sonar-project.properties file..."
cat <<EOL > ${PROJECT_DIR}/sonar-project.properties
# Required metadata
sonar.projectKey=${PROJECT_NAME}
sonar.projectName=${PROJECT_NAME}
sonar.projectVersion=1.0
sonar.login=admin
sonar.password=admin
sonar.updatecenter.activate=false
sonar.telemetry.enable=false

# Encoding of the source code
sonar.sourceEncoding=UTF-8

# SonarQube server URL
sonar.host.url=http://${container_ip}:${SONARQUBE_PORT}
EOL

# Pull SonarScanner Docker image
echo "Pulling SonarScanner Docker image..."
docker pull sonarsource/sonar-scanner-cli

sleep 30

# Run SonarScanner
# echo "Running SonarScanner..."
# docker run --rm -e SONAR_HOST_URL="http://${container_ip}:${SONARQUBE_PORT}" \
#   -v ${PROJECT_DIR}:/usr/src \
#   sonarsource/sonar-scanner-cli

unique_token=$(date +%s%N | md5sum | cut -d ' ' -f 1)

cli_token=$(curl -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "name=${unique_token}" -u admin:admin http://localhost:9000/api/user_tokens/generate | grep -o '"squ[^"]*"' | awk -F\" '{print $2}')

dotnet tool install --global dotnet-sonarscanner --version 6.2.0
dotnet sonarscanner begin /key:${PROJECT_NAME} //d:sonar.login=$cli_token
dotnet build ${PROJECT_DIR} --no-incremental
dotnet sonarscanner end //d:sonar.login=$cli_token

# Script end
echo "SonarQube analysis complete."

if [ ! -f "sonar-cnes-report-4.3.0.jar" ]; then
    echo "File does not exist."
    curl -L https://github.com/cnescatlab/sonar-cnes-report/releases/download/4.3.0/sonar-cnes-report-4.3.0.jar > sonar-cnes-report-4.3.0.jar
fi

java -jar sonar-cnes-report-4.3.0.jar -p ${PROJECT_NAME} -t $cli_token -s http://localhost:${SONARQUBE_PORT}

java -jar sonardotnetreport.jar http://localhost:${SONARQUBE_PORT} ${PROJECT_NAME} $cli_token ${PROJECT_NAME}-dotnet-issues.csv ${PROJECT_NAME}-dotnet-metrics.csv
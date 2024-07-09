This document summarizes the steps required to utilize SonarQube for static code analysis. It also briefs the steps to use Sonar CNES report plugin to generate reports on the code analysis. **The entire analysis will be done locally on your machine so make sure to do these steps on a machine that also consists of the codebase**.

The provided bash script can perform the analysis and generate the reports in one go if the following prerequisites are installed beforehand.


## Concerns with .NET projects

- The SonarQube CNES report plugin doesn't work well with .NET projects and doesnt generate proper reports.
- To circumvent this issue a custom plugin was developed to communicate with the SonarQube API to dump the list of issues as two extra CSV files.
- `./sonardotnetreport` folder contains the Java project responsible for generating said reports. A decision was made to use Java since the script anyways requires Java and it didnt make sense to ask users to install further prerequisites.


# Prerequisites

- [Rancher Desktop](https://rancherdesktop.io/)/ [Colima](https://github.com/abiosoft/colima) (or any equivalent Docker Container Management Engine)
  - If you have Docker desktop already installed, you could proceed, although it is not recommended to install docker desktop solely for running this script.
  - [Podman](https://podman.io/) can also be used but keep in mind the script needs to be updated to replace docker commands with podman commands
- [Java JDK 22](https://www.oracle.com/java/technologies/downloads/#jdk22-windows) OR any Java where JRE > 11

# Step 1

Open the provided script file and modify 2 environment variables at the top of the file before running the script.

```
# Modify variables
PROJECT_NAME="YOUR_PROJECT_NAME"
PROJECT_DIR="YOUR_PROJECT_DIR"
```

The PROJECT_NAME variable needs to be **lowercase and shouldn’t contain any special character or spaces**. The PROJECT_DIR variable needs to point to the location of the source code in your local machine. It needs to be an absolute path. You can point to the root folder of your source code, SonarQube can handle the rest.

If your project has multiple repos, then you will have to rerun the script with a different PROJECT_NAME variable and point to the correct directory using the PROJECT_DIR variable.

# Step 2

You can run the bash script in the terminal. You can use either of the following commands.

```
sh sonar.sh
```

OR

```
./sonar.sh
```

Once the command runs successfully, you will see a success message in the terminal and 6 files generated in the same folder as the script file.

```
2024-09-07-sonar-test-analysis-report.docx
2024-09-07-sonar-test-analysis-report.md
2024-09-07-sonar-test-issues-report.xlsx
2024-09-07-sonar-test-issues-report.csv
project-name-dotnet-issues.csv
project-name-dotnet-metrics.csv
```

These 6 files can be submitted as the final artifact.

## Note

The entire analysis can be run offline, internet access is only required to pull the docker images. The script can be rerun after pulling the images and can run the analysis offline. Telemetry data and marketplace are [disabled in the config](https://docs.sonarsource.com/sonarqube/9.8/instance-administration/telemetry/) so no internet access is required and SonarQube will not push any data to its servers. The script was run in a fully offline air gapped environment as a test run

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class App {

	private static String SONARQUBE_URL;
	private static String PROJECT_KEY;
	private static String AUTH_TOKEN;
	private static String CSV_ISSUES_FILE;
	private static String CSV_METRICS_FILE;
	private static final HttpClient client = HttpClient.newHttpClient();
	private static final ObjectMapper mapper = new ObjectMapper();

	public static void main(String[] args) {
		if (args.length < 3) {
			System.out.println(
					"Usage: java -jar SonarQubeClient.jar <SONARQUBE_URL> <PROJECT_KEY> <AUTH_TOKEN> <ISSUES_FILENAME> <METRICS_FILENAME>");
			System.exit(1);
		}

		SONARQUBE_URL = args[0];
		PROJECT_KEY = args[1];
		AUTH_TOKEN = args[2];
		CSV_ISSUES_FILE = args[3];
		CSV_METRICS_FILE = args[4];

		try {
			String analysisId = getProjectAnalysisDetails(PROJECT_KEY);
			System.out.println("Analysis ID: " + analysisId);

			JsonNode detailedAnalysis = getDetailedAnalysis(analysisId);
			System.out.println("Detailed Analysis:");
			System.out.println(detailedAnalysis.toPrettyString());

			JsonNode componentMeasures = getComponentMeasures(PROJECT_KEY);
			System.out.println("Component Measures (Letter Grades):");
			System.out.println(componentMeasures.toPrettyString());

			getIssuesAndSaveCsv(PROJECT_KEY, CSV_ISSUES_FILE);
			getMetricsAndSaveCsv(PROJECT_KEY, CSV_METRICS_FILE);

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static String getProjectAnalysisDetails(String projectKey) throws IOException, InterruptedException {
		String apiUrl = String.format("%s/api/project_analyses/search?project=%s&ps=1", SONARQUBE_URL, projectKey);
		HttpResponse<String> response = makeApiCall(apiUrl);

		JsonNode jsonNode = mapper.readTree(response.body());
		if (jsonNode.has("analyses") && jsonNode.get("analyses").size() > 0) {
			return jsonNode.get("analyses").get(0).get("key").asText();
		} else {
			throw new RuntimeException("No analysis data found for the given project.");
		}
	}

	private static JsonNode getDetailedAnalysis(String analysisId) throws IOException, InterruptedException {
		String apiUrl = String.format("%s/api/qualitygates/project_status?analysisId=%s", SONARQUBE_URL, analysisId);
		HttpResponse<String> response = makeApiCall(apiUrl);
		return mapper.readTree(response.body());
	}

	private static JsonNode getComponentMeasures(String projectKey) throws IOException, InterruptedException {
		String apiUrl = String.format(
				"%s/api/measures/component?component=%s&metricKeys=reliability_rating,security_rating,sqale_rating",
				SONARQUBE_URL, projectKey);
		HttpResponse<String> response = makeApiCall(apiUrl);
		return mapper.readTree(response.body()).get("component").get("measures");
	}

	private static void getIssuesAndSaveCsv(String projectKey, String csvFile)
			throws IOException, InterruptedException {
		int page = 1;
		int pageSize = 100;
		int total = 0;

		try (CSVWriter writer = new CSVWriter(new FileWriter(csvFile))) {
			String[] header = { "key", "severity", "component", "project", "message", "line", "status", "creationDate",
					"updateDate" };
			writer.writeNext(header);

			while (true) {
				String apiUrl = String.format("%s/api/issues/search?componentKeys=%s&p=%d&ps=%d", SONARQUBE_URL,
						projectKey, page, pageSize);
				HttpResponse<String> response = makeApiCall(apiUrl);
				JsonNode jsonNode = mapper.readTree(response.body());

				if (total == 0) {
					total = jsonNode.get("total").asInt();
				}

				JsonNode issues = jsonNode.get("issues");
				if (issues.size() == 0) {
					break;
				}

				for (JsonNode issue : issues) {
					List<String> record = new ArrayList<>();
					record.add(issue.get("key").asText());
					record.add(issue.get("severity").asText());
					record.add(issue.get("component").asText());
					record.add(issue.get("project").asText());
					record.add(issue.get("message").asText());
					record.add(issue.has("line") ? issue.get("line").asText() : "");
					record.add(issue.get("status").asText());
					record.add(issue.get("creationDate").asText());
					record.add(issue.get("updateDate").asText());
					writer.writeNext(record.toArray(new String[0]));
				}

				if (issues.size() < pageSize) {
					break;
				}
				page++;
			}

			System.out.println("Total issues fetched: " + total);
			System.out.println("Issues have been saved to " + CSV_ISSUES_FILE);
		}
	}

	private static void getMetricsAndSaveCsv(String projectKey, String csvFile)
			throws IOException, InterruptedException {
		String apiUrl = String.format(
				"%s/api/measures/component?component=%s&metricKeys=bugs,vulnerabilities,security_hotspots_reviewed,code_smells,coverage,duplicated_lines_density,ncloc",
				SONARQUBE_URL, projectKey);
		HttpResponse<String> response = makeApiCall(apiUrl);

		try (CSVWriter writer = new CSVWriter(new FileWriter(csvFile))) {
			String[] header = { "metric", "value" };
			writer.writeNext(header);

			JsonNode measures = mapper.readTree(response.body()).get("component").get("measures");
			for (JsonNode measure : measures) {
				String[] record = { measure.get("metric").asText(), measure.get("value").asText() };
				writer.writeNext(record);
			}
		}

		System.out.println("Metrics have been saved to " + csvFile);
	}

	private static HttpResponse<String> makeApiCall(String apiUrl) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl))
				.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((AUTH_TOKEN + ":").getBytes()))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() != 200) {
			throw new RuntimeException("Failed to fetch data from API. HTTP response code: " + response.statusCode());
		}

		return response;
	}
}

package com.capitalone.dashboard;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.capitalone.dashboard.client.dto.BasicProject;
import com.capitalone.dashboard.client.dto.Issue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

public class RallyUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(RallyUtil.class);

	private static final int INDEX_SUBSCRIPTION_NAME = 3;
	private static final int INDEX_WORKSPACE_NAME = 2;
	private static final int INDEX_PROJECT_NAME = 1;
	private static final int INDEX_RELEASE_NAME = 0;

	private static final int INT_PROJECT_AGE_IN_DAYS = 10000;
	private static final int INT_RELEASE_AGE_IN_DAYS = 10000;

	public static List<BasicProject> getProjects(RallyRestApi rallyRestApi) {
		List<BasicProject> lstBasicProject = new ArrayList<BasicProject>();
		try {
			QueryRequest qrySubscriptions = new QueryRequest("subscription");
			qrySubscriptions.setFetch(new Fetch("Workspaces", "Name"));
			QueryResponse respSubscriptions = rallyRestApi.query(qrySubscriptions);

			JsonArray lstSubscriptions = respSubscriptions.getResults();
			int intSubscriptionSize = lstSubscriptions.size();
			for (int intSubscriptionCtr = 0; intSubscriptionCtr < intSubscriptionSize; intSubscriptionCtr++) {
				JsonObject jsonSubscription = lstSubscriptions.get(intSubscriptionCtr).getAsJsonObject();

				String strSubscriptionName = jsonSubscription.get("Name").getAsString();
				JsonObject jsonWorkspaces = jsonSubscription.getAsJsonObject("Workspaces");

				QueryRequest qryWorkspaces = new QueryRequest(jsonWorkspaces);
				qryWorkspaces.setFetch(new Fetch("Name", "Projects"));

				QueryResponse respWorkspaces = rallyRestApi.query(qryWorkspaces);
				if (respWorkspaces.wasSuccessful()) {
					for (JsonElement workspace : respWorkspaces.getResults()) {
						JsonObject jsonWorkspace = workspace.getAsJsonObject();

						String strWorkspaceName = jsonWorkspace.get("Name").getAsString();
						JsonObject jsonProjects = jsonWorkspace.getAsJsonObject("Projects");

						QueryRequest qryProjects = new QueryRequest(jsonProjects);
						qryProjects.setFetch(new Fetch("Name", "Releases"));
						// qryProjects.setQueryFilter(new
						// QueryFilter("CreationDate", ">", "today-" +
						// INT_PROJECT_AGE_IN_DAYS));

						QueryResponse respProjects = rallyRestApi.query(qryProjects);
						if (respProjects.wasSuccessful()) {
							for (JsonElement respProject : respProjects.getResults()) {
								JsonObject jsonProject = respProject.getAsJsonObject();
								String strProjectName = jsonProject.get("Name").getAsString();
								JsonObject jsonReleases = jsonProject.getAsJsonObject("Releases");

								QueryRequest qryReleases = new QueryRequest(jsonReleases);
								qryReleases.setQueryFilter(new QueryFilter("State", "=", "Active")
										.and(new QueryFilter("ReleaseDate", ">", "today")));
								qryReleases.setFetch(new Fetch("Name", "ObjectID"
								// , "ReleaseDate"
								));

								QueryResponse respReleases = rallyRestApi.query(qryReleases);
								if (respReleases.wasSuccessful()) {
									for (JsonElement respRelease : respReleases.getResults()) {
										JsonObject jsonRelease = respRelease.getAsJsonObject();
										String strReleaseName = jsonRelease.get("Name").getAsString();
										String strReferenceURL = jsonRelease.get("_ref").getAsString();
										Long lngObjectID = new Long(jsonRelease.get("ObjectID").getAsString());
										// String strReleaseDate =
										// jsonRelease.get("ReleaseDate").getAsString();

										String[] arrConsolidatedProjectName = new String[4];
										arrConsolidatedProjectName[INDEX_SUBSCRIPTION_NAME] = strSubscriptionName;
										arrConsolidatedProjectName[INDEX_WORKSPACE_NAME] = strWorkspaceName;
										arrConsolidatedProjectName[INDEX_PROJECT_NAME] = strProjectName;
										arrConsolidatedProjectName[INDEX_RELEASE_NAME] = strReleaseName;

										String strConsolidatedProjectName = String.join(":", arrConsolidatedProjectName);

										BasicProject objBasicProject = new BasicProject(URI.create(strReferenceURL), null, lngObjectID, strConsolidatedProjectName);
										lstBasicProject.add(objBasicProject);
									}
								}
							}
						}
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("99 : RallyUtil.getProjects(...) IOException e : " + e);
		}
		return lstBasicProject;
	}

	public static List<Issue> getIssues(RallyRestApi restApi, List<BasicProject> lstBasicProjects) {
		List<Issue> lstIssues = new ArrayList<Issue>();
		try {
			for (BasicProject objBasicProject : lstBasicProjects) {
				String[] strNames = objBasicProject.getName().split(":");

				QueryRequest defectRequest = new QueryRequest("defect");
				defectRequest.setFetch(
						new Fetch("ObjectID", "Priority", "State", "Severity", "Name", "LastUpdateDate", "Iteration"));
				defectRequest.setQueryFilter(new QueryFilter("Project.Name", "=", strNames[INDEX_PROJECT_NAME])
						.and(new QueryFilter("Release.Name", "=", strNames[INDEX_RELEASE_NAME]))
//						.and(new QueryFilter("Workspace.Name", "=", strWorkspaceName))
//						.and(new QueryFilter("Subscription.Name", "=", strSubscriptionName))
						.and(new QueryFilter("State", "<", "Closed")));
				QueryResponse respDefects = restApi.query(defectRequest);
				if (respDefects.wasSuccessful()) {
					// System.out.println("Defect Count: " +
					// respDefects.getTotalResultCount());
					for (JsonElement projectDefect : respDefects.getResults()) {
						JsonObject jsonDefect = projectDefect.getAsJsonObject();
//						LOGGER.debug("Defect details = {}", jsonDefect.toString());
//System.out.println("Defect details = " + jsonDefect.toString());

						JsonElement jsonSprintName = jsonDefect.get("Iteration._refObjectName");
						JsonElement jsonSprintURL = jsonDefect.get("Iteration._ref");
						JsonElement jsonSprintID = jsonDefect.get("Iteration._refObjectUUID");

						String strSprintName = ((jsonSprintName == null) ? "" : jsonSprintName.getAsString());
						String strSprintURL = ((jsonSprintURL == null) ? "" : jsonSprintURL.getAsString());
						String strSprintID = ((jsonSprintID == null) ? "" : jsonSprintID.getAsString());

						Issue objIssue = new Issue(URI.create(jsonDefect.get("_ref").getAsString()), null,
								new Long(jsonDefect.get("ObjectID").getAsString()), objBasicProject,
								jsonDefect.get("Priority").getAsString(), jsonDefect.get("Severity").getAsString(),
								jsonDefect.get("State").getAsString(), jsonDefect.get("Name").getAsString(),
								jsonDefect.get("LastUpdateDate").getAsString(),
								strSprintName,
								strSprintURL,
								strSprintID);
						lstIssues.add(objIssue);

//
//
//
//
//
//						JsonObject jsonRequirements = jsonDefect.getAsJsonObject("Requirement");
//						QueryRequest qryRequirements = new QueryRequest(jsonRequirements);
//						QueryResponse respRequirements = restApi.query(qryRequirements);
//						if (null != respRequirements && respRequirements.wasSuccessful()) {
//							for (JsonElement respRequirement : respRequirements.getResults()) {
//								JsonObject jsonRequirement = respRequirement.getAsJsonObject();
//								System.out.println("Requirement : " + jsonRequirement.getAsString());
//							}
//						}
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("138 : RallyUtil.getIssues(...) IOException e : " + e);
		}
		return lstIssues;
	}

	public static List<Issue> getIssues(RallyRestApi restApi, BasicProject objBasicProjects) {
		return getIssues(restApi, java.util.Arrays.asList(objBasicProjects));
	}

//	public static void main(String[] args) throws URISyntaxException, IOException {
//		RallyRestApi restApi = RestApiFactory.getRestApi();
//		try {
//			List<BasicProject> lstProjects = getProjects(restApi);
//			List<Issue> lstIssues = getIssues(restApi, lstProjects);
//
////			lstIssues.forEach(objIssue -> {
////				System.out.println(objIssue.toString());
////			});
//		} finally {
//			restApi.close();
//		}
//	}
}

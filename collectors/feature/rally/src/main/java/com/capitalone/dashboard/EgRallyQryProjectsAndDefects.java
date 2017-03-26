package com.capitalone.dashboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rallydev.rest.RallyRestApi;
import com.rallydev.rest.request.QueryRequest;
import com.rallydev.rest.response.QueryResponse;
import com.rallydev.rest.util.Fetch;
import com.rallydev.rest.util.QueryFilter;

import java.io.IOException;
import java.net.URISyntaxException;

public class EgRallyQryProjectsAndDefects {

	private static final int INT_PROJECT_AGE_IN_DAYS = 10000;
	private static final int INT_RELEASE_AGE_IN_DAYS = 10000;
    public static void main(String[] args) throws URISyntaxException, IOException {

        //Create and configure a new instance of RallyRestApi
        //(Server, username, password and proxy settings configured in Factory)
        RallyRestApi restApi = RestApiFactory.getRestApi();

        try {
            //Get a story with defects
            System.out.println("\nQuerying for projects with defects...");
//            QueryRequest storiesWithDefects = new QueryRequest("hierarchicalrequirement");
            QueryRequest qrySubscriptions = new QueryRequest("subscription");
//            storiesWithDefects.setQueryFilter(new QueryFilter("Defects.ObjectID", "!=", null));
            qrySubscriptions.setFetch(new Fetch("Workspaces", "Name"));
            QueryResponse respSubscriptions = restApi.query(qrySubscriptions);

            JsonArray lstSubscriptions = respSubscriptions.getResults();
            int intSubscriptionSize = lstSubscriptions.size();
            System.out.println("Subscriptions size : " + intSubscriptionSize);
            for (int intSubscriptionCtr = 0; intSubscriptionCtr < intSubscriptionSize; intSubscriptionCtr++) {
	            JsonObject jsonSubscription = lstSubscriptions.get(intSubscriptionCtr).getAsJsonObject();

	            //Inspect the defects collection
	            JsonObject jsonWorkspaces = jsonSubscription.getAsJsonObject("Workspaces");
				System.out.println(String.format("Subscription Name = %s: Workspace Count =%d",
						jsonSubscription.get("Name"), jsonWorkspaces.get("Count").getAsInt()));
	
	            //Query the defects collection
	            QueryRequest qryWorkspaces = new QueryRequest(jsonWorkspaces);
	            qryWorkspaces.setFetch(new Fetch("Name", "Projects"));

	            QueryResponse respWorkspaces = restApi.query(qryWorkspaces);
	            if (respWorkspaces.wasSuccessful()) {
	                for (JsonElement workspace : respWorkspaces.getResults()) {
	                    JsonObject jsonWorkspace = workspace.getAsJsonObject();

	    	            JsonObject jsonProjects = jsonWorkspace.getAsJsonObject("Projects");
	    	            System.out.println(String.format("Workspace = %s: Projects Count = %d", jsonWorkspace.get("Name"), jsonProjects.get("Count").getAsInt()));

	    	            QueryRequest qryProjects = new QueryRequest(jsonProjects);
	    	            qryProjects.setFetch(new Fetch("Name"));
//	    	            qryProjects.setQueryFilter(new QueryFilter("CreationDate", ">", "today-" + INT_PROJECT_AGE_IN_DAYS));

	    	            QueryResponse respProjects = restApi.query(qryProjects);
	    	            if (respProjects.wasSuccessful()) {
	    	            	System.out.println("Project Count after filtering: " + respProjects.getTotalResultCount());
	    	                for (JsonElement respProject : respProjects.getResults()) {
	    	                    JsonObject jsonProject = respProject.getAsJsonObject();
	    	                    String strProjectName = jsonProject.get("Name").getAsString();
	    	                    System.out.println(String.format("Project Name: %s", strProjectName));
//	    	    	            System.out.println(String.format("Project: %s", jsonProject.toString()));

	    	                    QueryRequest releaseRequest = new QueryRequest("release");
	    	                    releaseRequest.setFetch(new Fetch("Name", "State"));
								releaseRequest.setQueryFilter(new QueryFilter("Project.Name", "=", strProjectName)
//										.and(new QueryFilter("ReleaseDate", ">", "today-" + INT_RELEASE_AGE_IN_DAYS))
										);
	    	                    QueryResponse responseReleases = restApi.query(releaseRequest);
	    	    	            if (responseReleases.wasSuccessful()) {
	    	    	            	System.out.println("Release Count: " + responseReleases.getTotalResultCount());
	    	    	                for (JsonElement responseRelease : responseReleases.getResults()) {
	    	    	                    JsonObject jsonRelease = responseRelease.getAsJsonObject();
	    	    	                    System.out.println(String.format("\tName=%s: State=%s",
	    	    	                            jsonRelease.get("Name").getAsString(),
	    	    	                            jsonRelease.get("State").getAsString()));
//	    	    	                    System.out.println(String.format("Release details = %s", jsonRelease.toString()));
	    	    	                }
	    	    	            }

//	    	                    QueryRequest defectRequest = new QueryRequest("defect");
//	    	                    defectRequest.setFetch(new Fetch("Priority", "State", "Severity", "Name"));
//								defectRequest.setQueryFilter(new QueryFilter("Project.Name", "=", strProjectName)
//										.and(new QueryFilter("State", "<", "Fixed")));
//	    	                    QueryResponse projectDefects = restApi.query(defectRequest);
//	    	    	            if (projectDefects.wasSuccessful()) {
//	    	    	            	System.out.println("Defect Count: " + projectDefects.getTotalResultCount());
//	    	    	                for (JsonElement result : projectDefects.getResults()) {
//	    	    	                    JsonObject defect = result.getAsJsonObject();
////	    	    	                    System.out.println(String.format("Defect details = %s", defect.toString()));
//	    	    	                    System.out.println(String.format("\tPriority=%s: Severity=%s: State=%s",
//	    	    	                            defect.get("Priority").getAsString(),
//	    	    	                            defect.get("Severity").getAsString(),
//	    	    	                            defect.get("State").getAsString()));
//	    	    	                }
//	    	    	            }
	    	                }
	    	            }
	                }
	            }
            }
        } finally {
            //Release resources
            restApi.close();
        }
    }
}

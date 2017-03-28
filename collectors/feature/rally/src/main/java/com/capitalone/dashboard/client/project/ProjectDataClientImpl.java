package com.capitalone.dashboard.client.project;

import java.util.List;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.capitalone.dashboard.RallyUtil;
import com.capitalone.dashboard.client.dto.BasicProject;
import com.capitalone.dashboard.model.Scope;
import com.capitalone.dashboard.repository.FeatureCollectorRepository;
import com.capitalone.dashboard.repository.ScopeRepository;
import com.capitalone.dashboard.util.ClientUtil;
import com.capitalone.dashboard.util.FeatureCollectorConstants;
import com.capitalone.dashboard.util.FeatureSettings;
import com.rallydev.rest.RallyRestApi;

/**
 * This is the primary implemented/extended data collector for the feature
 * collector. This will get data from the source system, but will grab the
 * majority of needed data and aggregate it in a single, flat MongoDB collection
 * for consumption.
 * 
 * @author kfk884
 * 
 */
public class ProjectDataClientImpl implements ProjectDataClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectDataClientImpl.class);
	private static final ClientUtil TOOLS = ClientUtil.getInstance();
	
	private final FeatureSettings featureSettings;
	private final ScopeRepository projectRepo;
	private final FeatureCollectorRepository featureCollectorRepository;
	private final RallyRestApi rallyRestApi;

	/**
	 * Extends the constructor from the super class.
	 *
	 */
	public ProjectDataClientImpl(FeatureSettings featureSettings, ScopeRepository projectRepository, 
			FeatureCollectorRepository featureCollectorRepository, RallyRestApi rallyRestApi) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Constructing data collection for the feature widget, project-level data...");
		}

		this.featureSettings = featureSettings;
		this.projectRepo = projectRepository;
		this.featureCollectorRepository = featureCollectorRepository;
		this.rallyRestApi = rallyRestApi;
	}

	/**
	 * Explicitly updates queries for the source system, and initiates the
	 * update to MongoDB from those calls.
	 */
	public int updateProjectInformation() {
		int count = 0;
		
		List<BasicProject> projects = RallyUtil.getProjects(rallyRestApi);
		
		if (projects != null && !projects.isEmpty()) {
			updateMongoInfo(projects);
			count += projects.size();
		}
		
		return count;
	}
	
	/**
	 * Updates the MongoDB with a JSONArray received from the source system
	 * back-end with story-based data.
	 * 
	 * @param currentPagedRallyRs
	 *            A list response of Rally issues from the source system
	 */
	private void updateMongoInfo(List<BasicProject> currentPagedRallyRs) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Size of paged Rally response: " + (currentPagedRallyRs == null? 0 : currentPagedRallyRs.size()));
		}

		if (currentPagedRallyRs != null) {
			ObjectId rallyCollectorId = featureCollectorRepository.findByName(FeatureCollectorConstants.RALLY).getId();
			for (BasicProject rallyScope : currentPagedRallyRs) {
				String scopeId = TOOLS.sanitizeResponse(rallyScope.getId());

				Scope scope = findOneScope(scopeId);

				if (scope == null) {
					scope = new Scope();
				}
				scope.setCollectorId(rallyCollectorId);
				scope.setpId(TOOLS.sanitizeResponse(scopeId));
				scope.setName(TOOLS.sanitizeResponse(rallyScope.getName()));
				scope.setBeginDate("");
				scope.setEndDate("");
				scope.setChangeDate("");
				scope.setAssetState("Active");
				scope.setIsDeleted("False");
				scope.setProjectPath(TOOLS.sanitizeResponse(rallyScope.getName()));
				projectRepo.save(scope);
			}
		}
	}
	
	/**
	 * Retrieves the maximum change date for a given query.
	 * 
	 * @return A list object of the maximum change date
	 */
	public String getMaxChangeDate() {
		String data = null;
		try {
			List<Scope> response = projectRepo
					.findTopByCollectorIdAndChangeDateGreaterThanOrderByChangeDateDesc(
							featureCollectorRepository.findByName(FeatureCollectorConstants.RALLY).getId(),
							featureSettings.getDeltaStartDate());
			if ((response != null) && !response.isEmpty()) {
				data = response.get(0).getChangeDate();
			}
		} catch (Exception e) {
			LOGGER.error("There was a problem retrieving or parsing data from the local repository while retrieving a max change date\nReturning null");
		}

		return data;
	}
	
	/**
	 * Find the current collector item for the rally team id
	 * 
	 * @param teamId	the team id
	 * @return			the collector item if it exists or null
	 */
	private Scope findOneScope(String scopeId) {
		List<Scope> scopes = projectRepo.getScopeIdById(scopeId);
		
		// Not sure of the state of the data
		if (scopes.size() > 1) {
			LOGGER.warn("More than one collector item found for scopeId " + scopeId);
		}
		
		if (!scopes.isEmpty()) {
			return scopes.get(0);
		}
		
		return null;
	}
}

/*************************DA-BOARD-LICENSE-START*********************************
 * Copyright 2014 CapitalOne, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************DA-BOARD-LICENSE-END*********************************/

package com.capitalone.dashboard.client.story;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.capitalone.dashboard.RallyUtil;
import com.capitalone.dashboard.client.dto.BasicProject;
import com.capitalone.dashboard.client.dto.Issue;
import com.capitalone.dashboard.model.Feature;
import com.capitalone.dashboard.model.FeatureStatus;
import com.capitalone.dashboard.repository.FeatureCollectorRepository;
import com.capitalone.dashboard.repository.FeatureRepository;
import com.capitalone.dashboard.repository.ScopeOwnerRepository;
import com.capitalone.dashboard.util.ClientUtil;
import com.capitalone.dashboard.util.CoreFeatureSettings;
import com.capitalone.dashboard.util.DateUtil;
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
public class StoryDataClientImpl implements StoryDataClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(StoryDataClientImpl.class);
	private static final ClientUtil TOOLS = ClientUtil.getInstance();

	public static final String STATE_FIXED = "Fixed";
	public static final String STATE_OPEN = "Open";
	public static final String STATE_SUBMITTED = "Submitted";
	public static final String STATE_CLOSED = "Closed";

//	private static final Comparator<Sprint> SPRINT_COMPARATOR = new Comparator<Sprint>() {
//		@Override
//		public int compare(Sprint o1, Sprint o2) {
//			int cmp1 = ObjectUtils.compare(o1.getStartDateStr(), o2.getStartDateStr());
//			
//			if (cmp1 != 0) {
//				return cmp1;
//			}
//			
//			return ObjectUtils.compare(o1.getEndDateStr(), o2.getEndDateStr());
//		}
//	};
	private static final String FEATURE_TYPE_ID_STORY = "0";
	private static final String FEATURE_TYPE_NAME_STORY = "Story";
	
	// works with ms too (just ignores them)
	private final DateFormat SETTINGS_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	private final FeatureSettings featureSettings;
	private final FeatureRepository featureRepo;
	private final FeatureCollectorRepository featureCollectorRepository;
	private final ScopeOwnerRepository teamRepository;
	private final RallyRestApi rallyRestApi;

	// epicId : list of epics
	private Map<String, Issue> epicCache;
	private Set<String> todoCache;
	private Set<String> inProgressCache;
	private Set<String> doneCache;

	/**
	 * Extends the constructor from the super class.
	 */
	public StoryDataClientImpl(CoreFeatureSettings coreFeatureSettings, FeatureSettings featureSettings, 
			FeatureRepository featureRepository, FeatureCollectorRepository featureCollectorRepository, ScopeOwnerRepository teamRepository,
			RallyRestApi rallyRestApi) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Constructing data collection for the feature widget, story-level data...");
		}

		this.featureSettings = featureSettings;
		this.featureRepo = featureRepository;
		this.featureCollectorRepository = featureCollectorRepository;
		this.teamRepository = teamRepository;
		this.rallyRestApi = rallyRestApi;
		
		this.epicCache = new HashMap<>();
		
		todoCache = buildStatusCache(coreFeatureSettings.getTodoStatuses());
		inProgressCache = buildStatusCache(coreFeatureSettings.getDoingStatuses());
		doneCache = buildStatusCache(coreFeatureSettings.getDoneStatuses());
	}

	/**
	 * Explicitly updates queries for the source system, and initiates the
	 * update to MongoDB from those calls.
	 */
	public int updateStoryInformation() {
		int count = 0;
		epicCache.clear(); // just in case class is made static w/ spring in future
//		
//		//long startDate = featureCollectorRepository.findByName(FeatureCollectorConstants.JIRA).getLastExecuted();
//		
//		String startDateStr = featureSettings.getDeltaStartDate();
//		String maxChangeDate = getMaxChangeDate();
//		if (maxChangeDate != null) {
//			startDateStr = maxChangeDate;
//		}
//		
//		startDateStr = getChangeDateMinutePrior(startDateStr);
//		long startTime;
//		try {
//			startTime = SETTINGS_DATE_FORMAT.parse(startDateStr).getTime();
//		} catch (ParseException e) {
//			throw new RuntimeException(e);
//		}
//		
//		int pageSize = jiraClient.getPageSize();

		updateStatuses();

		long queryStart = System.currentTimeMillis();
		List<BasicProject> lstProjects = RallyUtil.getProjects(rallyRestApi);
		List<Issue> lstIssues = RallyUtil.getIssues(rallyRestApi, lstProjects);
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Projects and defects query took " + (System.currentTimeMillis() - queryStart) + " ms");

		if (lstIssues != null && !lstIssues.isEmpty()) {
			updateMongoInfo(lstIssues);
			count += lstIssues.size();
		}

		return count;
	}

	/**
	 * Updates the MongoDB with a JSONArray received from the source system
	 * back-end with story-based data.
	 * 
	 * @param lstIssues
	 *            A list response of Rally issues from the source system
	 */
	@SuppressWarnings({ "PMD.AvoidDeeplyNestedIfStmts", "PMD.NPathComplexity" })
	private void updateMongoInfo(List<Issue> lstIssues) {
		LOGGER.debug("Start : StoryDataClientImpl.updateMongoInfo(...) : Size of paged Rally response: {}", (lstIssues == null? 0 : lstIssues.size()));

		if (lstIssues != null) {
			List<Feature> featuresToSave = new ArrayList<Feature>();

			Map<String, String> issueEpics = new HashMap<String, String>();

			ObjectId rallyFeatureId = featureCollectorRepository.findByName(FeatureCollectorConstants.RALLY).getId();
			
			for (Issue issue : lstIssues) {
				String issueId = TOOLS.sanitizeResponse(issue.getId());
				
				Feature feature = findOneFeature(issueId);
				if (feature == null) {
					 feature = new Feature();
				}

				if (LOGGER.isDebugEnabled())
					LOGGER.debug(String.format("[%-12s] %s", TOOLS.sanitizeResponse(issue.getKey()), TOOLS.sanitizeResponse(issue.getName())));

				// collectorId
				feature.setCollectorId(rallyFeatureId);

				// ID
				feature.setsId(TOOLS.sanitizeResponse(issue.getId()));

				// Type
				feature.setsTypeId(FEATURE_TYPE_ID_STORY);
				feature.setsTypeName(FEATURE_TYPE_NAME_STORY);

				processFeatureData(feature, issue);

//				// delay processing epic data for performance
//				if (epic != null && epic.getValue() != null && !TOOLS.sanitizeResponse(epic.getValue()).isEmpty()) {
//					issueEpics.put(feature.getsId(), TOOLS.sanitizeResponse(epic.getValue()));
//				}

				processSprintData(feature, issue);

				processAssigneeData(feature, issue);
				
				featuresToSave.add(feature);
			}

			LOGGER.debug("Processing epic data");
			
//			long epicStartTime = System.currentTimeMillis();
//			Collection<String> epicsToLoad = issueEpics.values();
//			loadEpicData(epicsToLoad);
//
//			for (Feature feature : featuresToSave) {
//				String epicKey = issueEpics.get(feature.getsId());
//
//				processEpicData(feature, epicKey);
//			}
//			if (LOGGER.isDebugEnabled()) {
//				LOGGER.debug("Processing epic data took " + (System.currentTimeMillis() - epicStartTime) + " ms");
//			}

			// Saving back to MongoDB
			featureRepo.save(featuresToSave);
		}
	}
	
	@SuppressWarnings({"PMD.ExcessiveMethodLength", "PMD.NPathComplexity"})
	private void processFeatureData(Feature feature, Issue issue) {
		BasicProject project = issue.getProject();
		String status = this.toCanonicalFeatureStatus(issue.getState());
		String changeDate = issue.getLastUpdateDate();

		feature.setsNumber(TOOLS.sanitizeResponse(issue.getKey()));
		feature.setsName(TOOLS.sanitizeResponse(issue.getName()));
		feature.setsStatus(TOOLS.sanitizeResponse(status));
		feature.setsState(TOOLS.sanitizeResponse(status));
		feature.setChangeDate(TOOLS.toCanonicalDate(TOOLS.sanitizeResponse(changeDate)));
		feature.setsProjectID(TOOLS.sanitizeResponse(project.getId()));
		feature.setsProjectName(TOOLS.sanitizeResponse(project.getName()));
        feature.setsUrl(issue.getSelf().toString());

        feature.setsEstimateTime(0);
		feature.setsEstimate("0");
		feature.setIsDeleted("False");
		feature.setsProjectBeginDate("");
		feature.setsProjectEndDate("");
		feature.setsProjectChangeDate("");
		feature.setsProjectState("");
		feature.setsProjectIsDeleted("False");
		feature.setsProjectPath("");
		feature.setsTeamChangeDate("");
		feature.setsTeamAssetState("");
		feature.setsTeamIsDeleted("False");
		feature.setsOwnersState(Arrays.asList("Active"));
		feature.setsOwnersChangeDate(TOOLS.toCanonicalList(Collections.<String>emptyList()));
		feature.setsOwnersIsDeleted(TOOLS.toCanonicalList(Collections.<String>emptyList()));
	}

//	private void processEpicData(Feature feature, String epicKey) {
//		if (epicKey != null && !epicKey.isEmpty()) {
//			Issue epicData = getEpicData(epicKey);
//			if (epicData != null) {
//				String epicId = epicData.getId().toString();
//				String epicNumber = epicData.getKey().toString();
//				String epicName = epicData.getSummary().toString();
//				String epicBeginDate = epicData.getCreationDate().toString();
//				String epicStatus = epicData.getStatus().getName();
//	
//				// sEpicID
//				feature.setsEpicID(TOOLS.sanitizeResponse(epicId));
//	
//				// sEpicNumber
//				feature.setsEpicNumber(TOOLS.sanitizeResponse(epicNumber));
//	
//				// sEpicName
//				feature.setsEpicName(TOOLS.sanitizeResponse(epicName));
//				
//				// sEpicUrl (Example: 'http://my.jira.com/browse/KEY-1001')
//		        feature.setsEpicUrl(featureSettings.getRallyBaseUrl() 
//		                + (featureSettings.getRallyBaseUrl().substring(featureSettings.getRallyBaseUrl().length()-1).equals("/") ? "" : "/")
//		                + "browse/" + TOOLS.sanitizeResponse(epicNumber));
//	
//				// sEpicBeginDate - mapped to create date
//				if ((epicBeginDate != null) && !(epicBeginDate.isEmpty())) {
//					feature.setsEpicBeginDate(TOOLS.toCanonicalDate(
//							TOOLS.sanitizeResponse(epicBeginDate)));
//				} else {
//					feature.setsEpicBeginDate("");
//				}
//	
//				// sEpicEndDate
//				if (epicData.getDueDate() != null) {
//					feature.setsEpicEndDate(TOOLS.toCanonicalDate(
//							TOOLS.sanitizeResponse(epicData.getDueDate())));
//				} else {
//					feature.setsEpicEndDate("");
//				}
//	
//				// sEpicAssetState
//				if (epicStatus != null) {
//					feature.setsEpicAssetState(TOOLS.sanitizeResponse(epicStatus));
//				} else {
//					feature.setsEpicAssetState("");
//				}
//			} else {
//				feature.setsEpicID("");
//				feature.setsEpicNumber("");
//				feature.setsEpicName("");
//				feature.setsEpicBeginDate("");
//				feature.setsEpicEndDate("");
//				feature.setsEpicType("");
//				feature.setsEpicAssetState("");
//				feature.setsEpicChangeDate("");
//			}
//		} else {
//			feature.setsEpicID("");
//			feature.setsEpicNumber("");
//			feature.setsEpicName("");
//			feature.setsEpicBeginDate("");
//			feature.setsEpicEndDate("");
//			feature.setsEpicType("");
//			feature.setsEpicAssetState("");
//			feature.setsEpicChangeDate("");
//		}
//		
//		// sEpicType - does not exist in jira
//		feature.setsEpicType("");
//
//		// sEpicChangeDate - does not exist in jira
//		feature.setsEpicChangeDate("");
//
//		// sEpicIsDeleted - does not exist in Jira
//		feature.setsEpicIsDeleted("False");
//	}

	@SuppressWarnings("PMD.NPathComplexity")
	private void processSprintData(Feature feature, Issue issue) {
		if (issue != null && issue.getStrSprintName() != null && !"".equals(issue.getStrSprintName())) {
			try {
				feature.setsSprintID(String.valueOf(issue.getStrSprintID()));
				if (issue.getStrSprintName() != null)
					feature.setsSprintName(issue.getStrSprintName());
				else
					feature.setsSprintName("");

				// sSprintUrl (Example: 'https://rally1.rallydev.com/slm/webservice/v2.0/iteration/123456')
		        feature.setsSprintUrl(issue.getStrSprintURL());

				feature.setsSprintBeginDate("");
				feature.setsSprintEndDate("");
				feature.setsSprintAssetState("");
			} catch (RuntimeException e) {
				LOGGER.error("Failed to obtain sprint data from " + e);
			}
		} else {
			// Issue #678 - leave sprint blank. Not having a sprint does not imply kanban
			// as a story on a scrum board without a sprint is really on the backlog
			feature.setsSprintID("");
			feature.setsSprintName("");
			feature.setsSprintBeginDate("");
			feature.setsSprintEndDate("");
			feature.setsSprintAssetState("");
		}

		feature.setsSprintChangeDate("");
		feature.setsSprintIsDeleted("False");
	}
	
	private void processAssigneeData(Feature feature, Issue issue) {
//		if (assignee != null) {
//			// sOwnersID
//			List<String> assigneeKey = new ArrayList<String>();
//			// sOwnersShortName
//			// sOwnersUsername
//			List<String> assigneeName = new ArrayList<String>();
//			if (!assignee.getName().isEmpty() && (assignee.getName() != null)) {
//				assigneeKey.add(TOOLS.sanitizeResponse(assignee.getName()));
//				assigneeName.add(TOOLS.sanitizeResponse(assignee.getName()));
//
//			} else {
//				assigneeKey = new ArrayList<String>();
//				assigneeName = new ArrayList<String>();
//			}
//			feature.setsOwnersShortName(assigneeName);
//			feature.setsOwnersUsername(assigneeName);
//			feature.setsOwnersID(assigneeKey);
//
//			// sOwnersFullName
//			List<String> assigneeDisplayName = new ArrayList<String>();
//			if (!assignee.getDisplayName().isEmpty() && (assignee.getDisplayName() != null)) {
//				assigneeDisplayName.add(TOOLS.sanitizeResponse(assignee.getDisplayName()));
//			} else {
//				assigneeDisplayName.add("");
//			}
//			feature.setsOwnersFullName(assigneeDisplayName);
//		} else {
			feature.setsOwnersUsername(new ArrayList<String>());
			feature.setsOwnersShortName(new ArrayList<String>());
			feature.setsOwnersID(new ArrayList<String>());
			feature.setsOwnersFullName(new ArrayList<String>());
//		}
	}

	/**
	 * ETL for converting any number of custom Rally statuses to a reduced list
	 * of generally logical statuses used by Hygieia
	 * 
	 * @param nativeStatus
	 *            The status label as native to Rally
	 * @return A Hygieia-canonical status, as defined by a Core enum
	 */
	private String toCanonicalFeatureStatus(String nativeStatus) {
		// default to backlog
		String canonicalStatus = FeatureStatus.BACKLOG.getStatus();
		
		if (nativeStatus != null) {
			String nsLower = nativeStatus.toLowerCase(Locale.getDefault());
			
			if (todoCache.contains(nsLower)) {
				canonicalStatus = FeatureStatus.BACKLOG.getStatus();
			} else if (inProgressCache.contains(nsLower)) {
				canonicalStatus = FeatureStatus.IN_PROGRESS.getStatus();
			} else if (doneCache.contains(nsLower)) {
				canonicalStatus = FeatureStatus.DONE.getStatus();
			}
		}
		
		return canonicalStatus;
	}
	
	/**
	 * Retrieves the maximum change date for a given query.
	 * 
	 * @return A list object of the maximum change date
	 */
	public String getMaxChangeDate() {
		String data = null;

		try {
			List<Feature> response = featureRepo
					.findTopByCollectorIdAndChangeDateGreaterThanOrderByChangeDateDesc(
							featureCollectorRepository.findByName(FeatureCollectorConstants.RALLY).getId(),
							featureSettings.getDeltaStartDate());
			if ((response != null) && !response.isEmpty()) {
				data = response.get(0).getChangeDate();
			}
		} catch (Exception e) {
			LOGGER.error("There was a problem retrieving or parsing data from the local "
					+ "repository while retrieving a max change date\nReturning null", e);
		}

		return data;
	}

//	private void loadEpicData(Collection<String> epicKeys) {
//		// No need to lookup items that are already cached
//		Set<String> epicsToLookup = new HashSet<>();
//		epicsToLookup.addAll(epicKeys);
//		epicsToLookup.removeAll(epicCache.keySet());
//		
//		List<String> epicsToLookupL = new ArrayList<>(epicsToLookup);
//		
//		if (!epicsToLookupL.isEmpty()) {
//			if (LOGGER.isDebugEnabled()) {
//				LOGGER.debug("Obtaining epic information for epics: " + epicsToLookupL);
//			}
//			
//			// Do this at most 50 at a time as jira doesn't seem to always work when there are a lot of items in an in clause
//			int maxEpicsToLookup = Math.min(featureSettings.getPageSize(), 50);
//			
//			for (int i = 0; i < epicsToLookupL.size(); i += maxEpicsToLookup) {
//				int endIdx = Math.min(i + maxEpicsToLookup, epicsToLookupL.size());
//				
//				List<String> epicKeysSub = epicsToLookupL.subList(i, endIdx);
//				
//				List<Issue> epics = jiraClient.getEpics(epicKeysSub);
//				
//				for (Issue epic : epics) {
//					String epicKey = epic.getKey();
//					
//					epicCache.put(epicKey, epic);
//				}
//			}
//		}
//	}

	/**
	 * Retrieves the related Epic to the current issue from Jira. To make this
	 * thread-safe, please synchronize and lock on the result of this method.
	 * 
	 * @param epicKey
	 *            A given Epic Key
	 * @return A valid Jira Epic issue object
	 */
//	private Issue getEpicData(String epicKey) {
//		if (epicCache.containsKey(epicKey)) {
//			return epicCache.get(epicKey);
//		} else {
//			Issue epic = jiraClient.getEpic(epicKey);
//			epicCache.put(epicKey, epic);
//			
//			return epic;
//		}
//	}

	private String getChangeDateMinutePrior(String changeDateISO) {
		int priorMinutes = this.featureSettings.getScheduledPriorMin();
		return DateUtil.toISODateRealTimeFormat(DateUtil.getDatePriorToMinutes(
				DateUtil.fromISODateTimeFormat(changeDateISO), priorMinutes));
	}
	
	private Feature findOneFeature(String featureId) {
		List<Feature> features = featureRepo.getFeatureIdById(featureId);
		
		// Not sure of the state of the data
		if (features.size() > 1) {
			LOGGER.warn("More than one collector item found for scopeId " + featureId);
		}
		
		if (!features.isEmpty()) {
			return features.get(0);
		}
		
		return null;
	}

//	private Map<String, IssueField> buildFieldMap(Iterable<IssueField> fields) {
//		Map<String, IssueField> rt = new HashMap<String, IssueField>();
//		
//		if (fields != null) {
//			for (IssueField issueField : fields) {
//				rt.put(issueField.getId(), issueField);
//			}
//		}
//		
//		return rt;
//	}
	
	private Set<String> buildStatusCache(List<String> statuses) {
		Set<String> rt = new HashSet<>();
		
		if (statuses != null) {
			for (String status : statuses) {
				rt.add(status.toLowerCase(Locale.getDefault()));
			}
		}
		
		return rt;
	}

	private void updateStatuses() {
		todoCache.add(STATE_OPEN);
		inProgressCache.add(STATE_SUBMITTED);
		doneCache.add(STATE_FIXED);
	}
}

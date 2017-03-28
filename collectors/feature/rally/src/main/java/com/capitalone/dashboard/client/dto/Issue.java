package com.capitalone.dashboard.client.dto;

import java.net.URI;

import com.google.common.base.Objects;

public class Issue {

	private final URI self;
	private final String key;
	private final Long id;
	private final BasicProject project;
	private final String strPriority;
	private final String strSeverity;
	private final String strState;
	private final String strName;
	private final String strLastUpdateDate;
	private final String strSprintName;
	private final String strSprintURL;
	private final String strSprintID;

	public Issue(URI self, String key, Long id, BasicProject project, String strPriority, String strSeverity,
			String strState, String strName, String strLastUpdateDate, String strSprintName, String strSprintURL, String strSprintID) {
		this.self = self;
		this.key = key;
		this.id = id;
		this.project = project;
		this.strPriority = strPriority;
		this.strSeverity = strSeverity;
		this.strState = strState;
		this.strName = strName;
		this.strLastUpdateDate = strLastUpdateDate;
		this.strSprintName = strSprintName;
		this.strSprintURL = strSprintURL;
		this.strSprintID = strSprintID;
	}

	public URI getSelf() {
		return self;
	}

	public String getKey() {
		return key;
	}

	public Long getId() {
		return id;
	}

	public BasicProject getProject() {
		return project;
	}

	public String getPriority() {
		return strPriority;
	}

	public String getSeverity() {
		return strSeverity;
	}

	public String getState() {
		return strState;
	}

	public String getName() {
		return strName;
	}

	public String getLastUpdateDate() {
		return strLastUpdateDate;
	}

	public String getStrSprintName() {
		return strSprintName;
	}

	public String getStrSprintURL() {
		return strSprintURL;
	}

	public String getStrSprintID() {
		return strSprintID;
	}

	@Override
	public String toString() {
		return getToStringHelper().toString();
	}

	protected Objects.ToStringHelper getToStringHelper() {
		return Objects.toStringHelper(this).
				add("self", self.toString()).
				add("key", key).
				add("id", id.toString()).
				add("project", project.toString()).
				add("priority", strPriority).
				add("severity", strSeverity).
				add("state", strState).
				add("name", strName).
				add("LastUpdateDate", strLastUpdateDate).
				add("SprintName", strSprintName).
				add("SprintID", strSprintID).
				add("SprintURL", strSprintURL);
	}
}

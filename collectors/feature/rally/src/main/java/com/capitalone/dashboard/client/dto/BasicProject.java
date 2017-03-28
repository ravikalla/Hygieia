package com.capitalone.dashboard.client.dto;

import java.net.URI;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

public class BasicProject {
	private final URI self;
	private final String key;
	@Nullable
	private final Long id;
	@Nullable
	private final String name;

	public BasicProject(final URI self, final String key, @Nullable final Long id, final @Nullable String name) {
		this.self = self;
		this.key = key;
		this.id = id;
		this.name = name;
	}

	public URI getSelf() {
		return self;
	}

	public String getKey() {
		return key;
	}

	@Nullable
	public String getName() {
		return name;
	}

	@Nullable
	public Long getId() {
		return id;
	}

	@Override
	public String toString() {
		return getToStringHelper().toString();
	}

	protected Objects.ToStringHelper getToStringHelper() {
		return Objects.toStringHelper(this).
				add("self", self).
				add("key", key).
				add("id", id).
				add("name", name);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof BasicProject) {
			BasicProject that = (BasicProject) obj;
			return Objects.equal(this.self, that.self)
					&& Objects.equal(this.name, that.name)
					&& Objects.equal(this.id, that.id)
					&& Objects.equal(this.key, that.key);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(self, name, id, key);
	}
}

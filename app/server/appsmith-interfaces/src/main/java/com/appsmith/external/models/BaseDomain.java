package com.appsmith.external.models;

import com.appsmith.external.helpers.Identifiable;
import com.appsmith.external.views.Views;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.querydsl.core.annotations.QueryTransient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * TODO :
 * Move BaseDomain back to appsmith-server.domain. This is done temporarily to create templates and providers in the same database as the server
 */
@Getter
@Setter
@ToString
@FieldNameConstants
public abstract class BaseDomain implements Persistable<String>, AppsmithDomain, Serializable, Identifiable {

    private static final long serialVersionUID = 7459916000501322517L;

    @Id
    @JsonView(Views.Public.class)
    private String id;

    @JsonView(Views.Internal.class)
    @Indexed
    @CreatedDate
    protected Instant createdAt;

    @JsonView(Views.Internal.class)
    @LastModifiedDate
    protected Instant updatedAt;

    @CreatedBy
    @JsonView(Views.Public.class)
    protected String createdBy;

    @LastModifiedBy
    @JsonView(Views.Public.class)
    protected String modifiedBy;

    /** @deprecated to rely only on `deletedAt` for all domain models.
     * This field only exists here because its removal will cause a huge diff on all entities in git-connected
     * applications. So, instead, we keep it, deprecated, query-transient (no corresponding field in Q* class),
     * no getter/setter methods and only use it for reflection-powered services, like the git sync
     * implementation. For all other practical purposes, this field doesn't exist.
     */
    @Deprecated(forRemoval = true)
    @JsonView(Views.Internal.class)
    @QueryTransient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    protected Boolean deleted = false;

    @JsonView(Views.Public.class)
    protected Instant deletedAt = null;

    @JsonView(Views.Internal.class)
    protected Set<Policy> policies = new HashSet<>();

    @Override
    @JsonView(Views.Public.class)
    public boolean isNew() {
        return this.getId() == null;
    }

    @QueryTransient
    @JsonView(Views.Internal.class)
    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Transient
    @JsonView(Views.Public.class)
    public Set<String> userPermissions = new HashSet<>();

    // This field will only be used for git related functionality to sync the action object across different instances.
    // This field will be deprecated once we move to the new git sync implementation.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonView(Views.Internal.class)
    String gitSyncId;

    public void sanitiseToExportDBObject() {
        this.setCreatedAt(null);
        this.setUpdatedAt(null);
        this.setUserPermissions(null);
        this.setPolicies(null);
        this.setCreatedBy(null);
        this.setModifiedBy(null);
    }

    public void makePristine() {
        // Set the ID to null for this domain object so that it is saved a new document in the database (as opposed to
        // updating an existing document). If it contains any policies, they are also reset.
        this.setId(null);
        this.setUpdatedAt(null);
        if (this.getPolicies() != null) {
            this.getPolicies().clear();
        }
    }

    /**
     * Prepares the domain for bulk write operation. It does the following:
     * 1. Populate an ID if it is not present
     * 2. Populate the createdAt and updatedAt fields as they'll not be generated by the bulk insert process
     */
    public void updateForBulkWriteOperation() {
        if (this.getId() == null) {
            this.setId(new ObjectId().toString());
        }
        if (this.getCreatedAt() == null) {
            this.setCreatedAt(Instant.now());
        }
        this.setUpdatedAt(Instant.now());
    }

    public static class Fields {}
}
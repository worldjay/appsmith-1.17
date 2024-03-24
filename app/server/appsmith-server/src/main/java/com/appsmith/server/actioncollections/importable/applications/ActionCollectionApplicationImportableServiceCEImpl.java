package com.appsmith.server.actioncollections.importable.applications;

import com.appsmith.external.models.DefaultResources;
import com.appsmith.server.actioncollections.base.ActionCollectionService;
import com.appsmith.server.constants.FieldName;
import com.appsmith.server.defaultresources.DefaultResourcesService;
import com.appsmith.server.domains.ActionCollection;
import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.Artifact;
import com.appsmith.server.domains.Context;
import com.appsmith.server.domains.NewPage;
import com.appsmith.server.dtos.ActionCollectionDTO;
import com.appsmith.server.dtos.ImportingMetaDTO;
import com.appsmith.server.dtos.MappedImportableResourcesDTO;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.helpers.DefaultResourcesUtils;
import com.appsmith.server.imports.importable.artifactbased.ArtifactBasedImportableServiceCE;
import com.appsmith.server.repositories.ActionCollectionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ActionCollectionApplicationImportableServiceCEImpl
        implements ArtifactBasedImportableServiceCE<ActionCollection, Application> {

    private final ActionCollectionRepository repository;
    private final DefaultResourcesService<ActionCollection> defaultResourcesService;
    private final DefaultResourcesService<ActionCollectionDTO> dtoDefaultResourcesService;
    private final ActionCollectionService actionCollectionService;

    @Override
    public List<String> getImportedContextNames(MappedImportableResourcesDTO mappedImportableResourcesDTO) {
        return mappedImportableResourcesDTO.getContextMap().values().stream()
                .distinct()
                .map(context -> ((NewPage) context).getUnpublishedPage().getName())
                .toList();
    }

    @Override
    public void renameContextInImportableResources(
            List<ActionCollection> actionCollectionList, String oldContextName, String newContextName) {
        for (ActionCollection actionCollection : actionCollectionList) {
            if (actionCollection.getUnpublishedCollection().getPageId().equals(oldContextName)) {
                actionCollection.getUnpublishedCollection().setPageId(newContextName);
            }
        }
    }

    @Override
    public Flux<ActionCollection> getExistingResourcesInCurrentArtifactFlux(Artifact artifact) {
        return repository.findByApplicationId(artifact.getId(), Optional.empty(), Optional.empty());
    }

    @Override
    public Flux<ActionCollection> getExistingResourcesInOtherBranchesFlux(
            String defaultArtifactId, String currentArtifactId) {
        return repository
                .findByDefaultApplicationId(defaultArtifactId, Optional.empty())
                .filter(actionCollection -> !Objects.equals(actionCollection.getApplicationId(), currentArtifactId));
    }

    @Override
    public Context updateContextInResource(
            Object dtoObject, Map<String, ? extends Context> contextMap, String fallbackDefaultContextId) {
        ActionCollectionDTO collectionDTO = (ActionCollectionDTO) dtoObject;

        if (StringUtils.isEmpty(collectionDTO.getPageId())) {
            collectionDTO.setPageId(fallbackDefaultContextId);
        }

        NewPage parentPage = (NewPage) contextMap.get(collectionDTO.getPageId());

        if (parentPage == null) {
            return null;
        }
        collectionDTO.setPageId(parentPage.getId());

        // Update defaultResources in actionCollectionDTO
        DefaultResources defaultResources = new DefaultResources();
        defaultResources.setPageId(parentPage.getDefaultResources().getPageId());
        collectionDTO.setDefaultResources(defaultResources);

        return parentPage;
    }

    @Override
    public void populateDefaultResources(
            ImportingMetaDTO importingMetaDTO,
            MappedImportableResourcesDTO mappedImportableResourcesDTO,
            Artifact artifact,
            ActionCollection branchedActionCollection,
            ActionCollection actionCollection) {
        actionCollection.setApplicationId(artifact.getId());

        if (artifact.getGitArtifactMetadata() != null) {
            if (branchedActionCollection != null) {
                defaultResourcesService.setFromOtherBranch(
                        actionCollection, branchedActionCollection, importingMetaDTO.getBranchName());
                dtoDefaultResourcesService.setFromOtherBranch(
                        actionCollection.getUnpublishedCollection(),
                        branchedActionCollection.getUnpublishedCollection(),
                        importingMetaDTO.getBranchName());
            } else {
                // This is the first action collection  we are saving with given gitSyncId
                // in this instance
                DefaultResources defaultResources = new DefaultResources();
                defaultResources.setApplicationId(
                        artifact.getGitArtifactMetadata().getDefaultArtifactId());
                defaultResources.setCollectionId(actionCollection.getId());
                defaultResources.setBranchName(importingMetaDTO.getBranchName());
                actionCollection.setDefaultResources(defaultResources);
            }
        } else {
            DefaultResources defaultResources = new DefaultResources();
            defaultResources.setApplicationId(artifact.getId());
            defaultResources.setCollectionId(actionCollection.getId());
            actionCollection.setDefaultResources(defaultResources);
        }
    }

    @Override
    public void createNewResource(
            ImportingMetaDTO importingMetaDTO, ActionCollection actionCollection, Context defaultContext) {
        if (!importingMetaDTO.getPermissionProvider().canCreateAction((NewPage) defaultContext)) {
            throw new AppsmithException(
                    AppsmithError.ACL_NO_RESOURCE_FOUND, FieldName.PAGE, ((NewPage) defaultContext).getId());
        }

        // this will generate the id and other auto generated fields e.g. createdAt
        actionCollection.updateForBulkWriteOperation();
        actionCollectionService.generateAndSetPolicies((NewPage) defaultContext, actionCollection);

        // create or update default resources for the action
        // values already set to defaultResources are kept unchanged
        DefaultResourcesUtils.createDefaultIdsOrUpdateWithGivenResourceIds(
                actionCollection, importingMetaDTO.getBranchName());

        // generate gitSyncId if it's not present
        if (actionCollection.getGitSyncId() == null) {
            actionCollection.setGitSyncId(actionCollection.getApplicationId() + "_" + new ObjectId());
        }
    }
}
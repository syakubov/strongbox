package org.carlspring.strongbox.providers.repository.group;

import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.configuration.ConfigurationUtils;
import org.carlspring.strongbox.storage.Storage;
import org.carlspring.strongbox.storage.repository.Repository;

import javax.inject.Inject;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

/**
 * @author Przemyslaw Fusik
 */
@Component
public class GroupRepositorySetCollector
{

    @Inject
    private ConfigurationManager configurationManager;

    public Set<Repository> collect(Repository groupRepository)
    {
        return collect(groupRepository, false);
    }

    public Set<Repository> collect(Repository groupRepository,
                                   boolean traverse)
    {
        Set<Repository> result = groupRepository.getGroupRepositories()
                                                .stream()
                                                .map(groupRepoId -> getRepository(groupRepository.getStorage(),
                                                                                  groupRepoId))
                                                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!traverse)
        {
            return result;
        }

        Set<Repository> traverseResult = new LinkedHashSet<>();
        for (Iterator<Repository> i = result.iterator(); i.hasNext(); )
        {
            Repository r = i.next();
            if (CollectionUtils.isEmpty(r.getGroupRepositories()))
            {
                traverseResult.add(r);
                continue;
            }

            i.remove();
            traverseResult.addAll(collect(r, true));
        }

        return traverseResult;
    }

    private Repository getRepository(Storage storage,
                                     String id)
    {
        String sId = ConfigurationUtils.getStorageId(storage.getId(), id);
        String rId = ConfigurationUtils.getRepositoryId(id);

        return configurationManager.getConfiguration().getStorage(sId).getRepository(rId);
    }

}

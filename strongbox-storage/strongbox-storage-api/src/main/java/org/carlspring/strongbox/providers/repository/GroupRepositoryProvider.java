package org.carlspring.strongbox.providers.repository;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.carlspring.strongbox.artifact.coordinates.ArtifactCoordinates;
import org.carlspring.strongbox.configuration.ConfigurationUtils;
import org.carlspring.strongbox.data.criteria.OQueryTemplate;
import org.carlspring.strongbox.data.criteria.Paginator;
import org.carlspring.strongbox.data.criteria.Predicate;
import org.carlspring.strongbox.data.criteria.QueryTemplate;
import org.carlspring.strongbox.data.criteria.Selector;
import org.carlspring.strongbox.domain.ArtifactEntry;
import org.carlspring.strongbox.providers.io.AbstractRepositoryProvider;
import org.carlspring.strongbox.providers.io.RepositoryFiles;
import org.carlspring.strongbox.providers.io.RepositoryPath;
import org.carlspring.strongbox.providers.io.RepositoryPathResolver;
import org.carlspring.strongbox.providers.repository.event.GroupRepositoryPathFetchEvent;
import org.carlspring.strongbox.providers.repository.group.GroupRepositorySetCollector;
import org.carlspring.strongbox.services.support.ArtifactRoutingRulesChecker;
import org.carlspring.strongbox.storage.Storage;
import org.carlspring.strongbox.storage.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author carlspring
 */
@Component
public class GroupRepositoryProvider extends AbstractRepositoryProvider
{

    private static final Logger logger = LoggerFactory.getLogger(GroupRepositoryProvider.class);

    private static final String ALIAS = "group";

    @Inject
    private ArtifactRoutingRulesChecker artifactRoutingRulesChecker;

    @Inject
    private HostedRepositoryProvider hostedRepositoryProvider;

    @Inject
    private GroupRepositorySetCollector groupRepositorySetCollector;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Inject
    private RepositoryPathResolver repositoryPathResolver;
    
    @Override
    public String getAlias()
    {
        return ALIAS;
    }

    @Override
    protected InputStream getInputStreamInternal(RepositoryPath path) throws IOException
    {
        return hostedRepositoryProvider.getInputStreamInternal(path);
    }

    @Override
    public RepositoryPath fetchPath(RepositoryPath repositoryPath)
        throws IOException
    {
        eventPublisher.publishEvent(new GroupRepositoryPathFetchEvent(repositoryPath));

        RepositoryPath result = resolvePathDirectlyFromGroupPathIfPossible(repositoryPath);
        if (result != null)
        {
            return result;
        }
        
        return resolvePathTraversal(repositoryPath);
    }
    
    protected RepositoryPath resolvePathTraversal(RepositoryPath repositoryPath) throws IOException
    {
        Repository groupRepository = repositoryPath.getRepository();
        Storage storage = groupRepository.getStorage();
        
        for (String storageAndRepositoryId : groupRepository.getGroupRepositories())
        {
            String sId = ConfigurationUtils.getStorageId(storage.getId(), storageAndRepositoryId);
            String rId = ConfigurationUtils.getRepositoryId(storageAndRepositoryId);

            Repository subRepository = getConfiguration().getStorage(sId).getRepository(rId);
            if (!subRepository.isInService())
            {
                continue;
            }
            
            RepositoryPath result = repositoryPathResolver.resolve(subRepository, repositoryPath);
            if (artifactRoutingRulesChecker.isDenied(groupRepository, result))
            {
                continue;
            }
            
            result = resolvePathFromGroupMemberOrTraverse(result);
            if (result == null)
            {
                continue;
            }
            
            logger.debug(String.format("Located artifact: [%s]", result));
            
            return result;
        }
        return null;
    }

    private RepositoryPath resolvePathDirectlyFromGroupPathIfPossible(final RepositoryPath artifactPath)
    {
        if (Files.exists(artifactPath))
        {
            return artifactPath;
        }
        return null;
    }

    protected RepositoryPath resolvePathFromGroupMemberOrTraverse(RepositoryPath repositoryPath)
        throws IOException
    {
        Repository repository = repositoryPath.getRepository();
        if (getAlias().equals(repository.getType()))
        {
            return resolvePathTraversal(repositoryPath);
        }
        
        RepositoryProvider provider = repositoryProviderRegistry.getProvider(repository.getType());
        try
        {
            return (RepositoryPath) provider.fetchPath(repositoryPath);
        }
        catch (IOException e)
        {
            logger.error(String.format("Failed to resolve path [%s]", repositoryPath));
            return null;
        }
    }

    @Override
    protected OutputStream getOutputStreamInternal(RepositoryPath repositoryPath)
    {
        // It should not be possible to write artifacts to a group repository.
        // A group repository should only serve artifacts that already exist
        // in the repositories within the group.

        throw new UnsupportedOperationException();
    }

    @Override
    public List<Path> search(String storageId,
                             String repositoryId,
                             Predicate predicate,
                             Paginator paginator)
    {
        logger.debug(String.format("Search in [%s]:[%s] ...", storageId, repositoryId));

        Map<ArtifactCoordinates, Path> resultMap = new LinkedHashMap<>();

        Storage storage = getConfiguration().getStorage(storageId);
        Repository groupRepository = storage.getRepository(repositoryId);
        Set<Repository> groupRepositorySet = groupRepositorySetCollector.collect(groupRepository);

        if (groupRepositorySet.isEmpty())
        {
            return new LinkedList<>();
        }

        int skip = paginator.getSkip();
        int limit = paginator.getLimit();

        int groupSize = groupRepositorySet.size();
        int groupSkip = (skip / (limit * groupSize)) * limit;
        int groupLimit = limit;

        skip = skip - groupSkip;

        outer: do
        {
            Paginator paginatorLocal = new Paginator();
            paginatorLocal.setLimit(groupLimit);
            paginatorLocal.setSkip(groupSkip);
            paginatorLocal.setProperty(paginator.getProperty());
            paginatorLocal.setOrder(paginator.getOrder());

            groupLimit = 0;

            for (Iterator<Repository> i = groupRepositorySet.iterator(); i.hasNext();)
            {
                Repository r = i.next();
                RepositoryProvider repositoryProvider = repositoryProviderRegistry.getProvider(r.getType());

                List<Path> repositoryResult = repositoryProvider.search(r.getStorage().getId(), r.getId(), predicate, paginatorLocal);
                if (repositoryResult.isEmpty())
                {
                    i.remove();
                    continue;
                }

                // count coordinates intersection
                groupLimit += repositoryResult.stream()
                                              .map((p) -> resultMap.put(getArtifactCoordinates(p),
                                                                        p))
                                              .filter(p ->  p != null)
                                              .collect(Collectors.toList())
                                              .size();

                //Break search iterations if we have reached enough list size.
                if (resultMap.size() >= limit + skip)
                {
                    break outer;
                }
            }
            groupSkip += limit;

            // Will iterate until there is no more coordinates intersection and
            // there is more search results within group repositories
        } while (groupLimit > 0 && !groupRepositorySet.isEmpty());

        LinkedList<Path> resultList = new LinkedList<>();
        if (skip >= resultMap.size())
        {
            return resultList;
        }
        resultList.addAll(resultMap.values());

        int toIndex = resultList.size() - skip > limit ? limit + skip : resultList.size();
        return resultList.subList(skip, toIndex);
    }

    private ArtifactCoordinates getArtifactCoordinates(Path p)
    {
        try
        {
            return RepositoryFiles.readCoordinates((RepositoryPath) p);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed to resolve ArtifactCoordinates for [%s]", p), e);
        }
    }

    @Override
    public Long count(String storageId,
                      String repositoryId,
                      Predicate predicate)
    {
        logger.debug(String.format("Count in [%s]:[%s] ...", storageId, repositoryId));
        
        Storage storage = getConfiguration().getStorage(storageId);

        Repository groupRepository = storage.getRepository(repositoryId);

        Predicate p = Predicate.empty();
        
        p.or(createPredicate(storageId, repositoryId, predicate));
        groupRepositorySetCollector.collect(groupRepository,
                                            true)
                                   .stream()
                                   .forEach(r -> p.or(createPredicate(r.getStorage().getId(), r.getId(), predicate)));                                                                                            

        Selector<ArtifactEntry> selector = new Selector<>(ArtifactEntry.class);
        selector.select("count(distinct(artifactCoordinates))").where(p);
        
        QueryTemplate<Long, ArtifactEntry> queryTemplate = new OQueryTemplate<>(entityManager);
        
        return queryTemplate.select(selector);

    }

    
}

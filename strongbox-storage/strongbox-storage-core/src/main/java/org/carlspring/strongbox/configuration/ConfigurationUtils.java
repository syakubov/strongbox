package org.carlspring.strongbox.configuration;

public class ConfigurationUtils
{

    public static String getStorageId(String storageId,
                                      String storageAndRepositoryId)
    {
        String[] storageAndRepositoryIdTokens = storageAndRepositoryId.split(":");

        return storageAndRepositoryIdTokens.length == 2 ? storageAndRepositoryIdTokens[0] : storageId;
    }

    public static String getRepositoryId(String storageAndRepositoryId)
    {
        String[] storageAndRepositoryIdTokens = storageAndRepositoryId.split(":");

        return storageAndRepositoryIdTokens[storageAndRepositoryIdTokens.length < 2 ? 0 : 1];
    }

    
}

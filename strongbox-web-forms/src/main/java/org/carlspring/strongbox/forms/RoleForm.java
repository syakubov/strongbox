package org.carlspring.strongbox.forms;

import org.carlspring.strongbox.validation.UniqueRoleName;

import javax.validation.constraints.NotEmpty;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;

/**
 * @author Pablo Tirado
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleForm
{

    @NotEmpty(message = "A name must be specified.")
    @UniqueRoleName(message = "Role is already registered.")
    private String name;

    private String description;

    private Set<String> privileges = Sets.newHashSet();

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Set<String> getPrivileges()
    {
        return privileges;
    }

    public void setPrivileges(Set<String> privileges)
    {
        this.privileges = privileges;
    }
}

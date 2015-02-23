/*
 * Copyright (C) 2013 Glyptodon LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.sourceforge.guacamole.net.auth.mariadb;


import com.google.inject.Inject;
import java.util.Set;
import org.glyptodon.guacamole.GuacamoleClientException;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.auth.ConnectionGroup;
import org.glyptodon.guacamole.net.auth.ConnectionGroup.Type;
import org.glyptodon.guacamole.net.auth.Directory;
import net.sourceforge.guacamole.net.auth.mariadb.dao.ConnectionGroupPermissionMapper;
import net.sourceforge.guacamole.net.auth.mariadb.model.ConnectionGroupPermissionKey;
import net.sourceforge.guacamole.net.auth.mariadb.service.ConnectionGroupService;
import net.sourceforge.guacamole.net.auth.mariadb.service.PermissionCheckService;
import org.glyptodon.guacamole.GuacamoleResourceNotFoundException;
import org.glyptodon.guacamole.GuacamoleUnsupportedException;
import org.mybatis.guice.transactional.Transactional;

/**
 * A MariaDB-based implementation of the connection group directory.
 *
 * @author James Muehlner
 */
public class ConnectionGroupDirectory implements Directory<String, ConnectionGroup>{

    /**
     * The user who this connection directory belongs to. Access is based on
     * his/her permission settings.
     */
    private AuthenticatedUser currentUser;

    /**
     * The ID of the parent connection group.
     */
    private Integer parentID;

    /**
     * Service for checking permissions.
     */
    @Inject
    private PermissionCheckService permissionCheckService;

    /**
     * Service managing connection groups.
     */
    @Inject
    private ConnectionGroupService connectionGroupService;

    /**
     * Service for manipulating connection group permissions in the database.
     */
    @Inject
    private ConnectionGroupPermissionMapper connectionGroupPermissionDAO;

    /**
     * Set the user and parentID for this directory.
     *
     * @param currentUser
     *     The user owning this connection group directory.
     *
     * @param parentID
     *     The ID of the parent connection group.
     */
    public void init(AuthenticatedUser currentUser, Integer parentID) {
        this.parentID = parentID;
        this.currentUser = currentUser;
    }

    @Transactional
    @Override
    public ConnectionGroup get(String identifier) throws GuacamoleException {

        // Get connection
        MariaDBConnectionGroup connectionGroup =
                connectionGroupService.retrieveConnectionGroup(identifier, currentUser);
        
        if(connectionGroup == null)
            return null;
        
        // Verify permission to use the parent connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (connectionGroup.getParentID(), currentUser, MariaDBConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify access is granted
        permissionCheckService.verifyConnectionGroupAccess(
                currentUser,
                connectionGroup.getConnectionGroupID(),
                MariaDBConstants.CONNECTION_GROUP_READ);

        // Return connection group
        return connectionGroup;

    }

    @Transactional
    @Override
    public Set<String> getIdentifiers() throws GuacamoleException {
        
        // Verify permission to use the connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (parentID, currentUser, MariaDBConstants.CONNECTION_GROUP_ORGANIZATIONAL);
        
        return permissionCheckService.retrieveConnectionGroupIdentifiers(currentUser, 
                parentID, MariaDBConstants.CONNECTION_GROUP_READ);
    }

    @Transactional
    @Override
    public void add(ConnectionGroup object) throws GuacamoleException {

        String name = object.getName().trim();
        if(name.isEmpty())
            throw new GuacamoleClientException("The connection group name cannot be blank.");
        
        Type type = object.getType();
        
        String mariaDBType = MariaDBConstants.getConnectionGroupTypeConstant(type);
        
        // Verify permission to create
        permissionCheckService.verifySystemAccess(currentUser,
                MariaDBConstants.SYSTEM_CONNECTION_GROUP_CREATE);
        
        // Verify permission to edit the parent connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser, 
                this.parentID, MariaDBConstants.CONNECTION_GROUP_UPDATE);
        
        // Verify permission to use the parent connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (parentID, currentUser, MariaDBConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify that no connection already exists with this name.
        MariaDBConnectionGroup previousConnectionGroup =
                connectionGroupService.retrieveConnectionGroup(name, parentID, currentUser);
        if(previousConnectionGroup != null)
            throw new GuacamoleClientException("That connection group name is already in use.");

        // Create connection group
        MariaDBConnectionGroup connectionGroup = connectionGroupService
                .createConnectionGroup(name, currentUser, parentID, mariaDBType);
        
        // Set the connection group ID
        object.setIdentifier(connectionGroup.getIdentifier());

        // Finally, give the current user full access to the newly created
        // connection group.
        ConnectionGroupPermissionKey newConnectionGroupPermission = new ConnectionGroupPermissionKey();
        newConnectionGroupPermission.setUser_id(currentUser.getUserID());
        newConnectionGroupPermission.setConnection_group_id(connectionGroup.getConnectionGroupID());

        // Read permission
        newConnectionGroupPermission.setPermission(MariaDBConstants.CONNECTION_GROUP_READ);
        connectionGroupPermissionDAO.insert(newConnectionGroupPermission);

        // Update permission
        newConnectionGroupPermission.setPermission(MariaDBConstants.CONNECTION_GROUP_UPDATE);
        connectionGroupPermissionDAO.insert(newConnectionGroupPermission);

        // Delete permission
        newConnectionGroupPermission.setPermission(MariaDBConstants.CONNECTION_GROUP_DELETE);
        connectionGroupPermissionDAO.insert(newConnectionGroupPermission);

        // Administer permission
        newConnectionGroupPermission.setPermission(MariaDBConstants.CONNECTION_GROUP_ADMINISTER);
        connectionGroupPermissionDAO.insert(newConnectionGroupPermission);

    }

    @Transactional
    @Override
    public void update(ConnectionGroup object) throws GuacamoleException {

        // If connection not actually from this auth provider, we can't handle
        // the update
        if (!(object instanceof MariaDBConnectionGroup))
            throw new GuacamoleUnsupportedException("Connection not from database.");

        MariaDBConnectionGroup mariaDBConnectionGroup = (MariaDBConnectionGroup) object;

        // Verify permission to update
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                mariaDBConnectionGroup.getConnectionGroupID(),
                MariaDBConstants.CONNECTION_GROUP_UPDATE);

        // Perform update
        connectionGroupService.updateConnectionGroup(mariaDBConnectionGroup);
    }

    @Transactional
    @Override
    public void remove(String identifier) throws GuacamoleException {

        // Get connection
        MariaDBConnectionGroup mariaDBConnectionGroup =
                connectionGroupService.retrieveConnectionGroup(identifier, currentUser);
        
        if(mariaDBConnectionGroup == null)
            throw new GuacamoleResourceNotFoundException("Connection group not found.");
        
        // Verify permission to use the parent connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (mariaDBConnectionGroup.getParentID(), currentUser, MariaDBConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify permission to delete
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                mariaDBConnectionGroup.getConnectionGroupID(),
                MariaDBConstants.CONNECTION_GROUP_DELETE);

        // Delete the connection group itself
        connectionGroupService.deleteConnectionGroup
                (mariaDBConnectionGroup.getConnectionGroupID());

    }

    @Override
    public void move(String identifier, Directory<String, ConnectionGroup> directory) 
            throws GuacamoleException {
        
        if(MariaDBConstants.CONNECTION_GROUP_ROOT_IDENTIFIER.equals(identifier))
            throw new GuacamoleUnsupportedException("The root connection group cannot be moved.");
        
        if(!(directory instanceof ConnectionGroupDirectory))
            throw new GuacamoleUnsupportedException("Directory not from database");
        
        Integer toConnectionGroupID = ((ConnectionGroupDirectory)directory).parentID;

        // Get connection group
        MariaDBConnectionGroup mariaDBConnectionGroup =
                connectionGroupService.retrieveConnectionGroup(identifier, currentUser);
        
        if(mariaDBConnectionGroup == null)
            throw new GuacamoleResourceNotFoundException("Connection group not found.");

        // Verify permission to update the connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                mariaDBConnectionGroup.getConnectionGroupID(),
                MariaDBConstants.CONNECTION_GROUP_UPDATE);
        
        // Verify permission to use the from connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (mariaDBConnectionGroup.getParentID(), currentUser, MariaDBConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify permission to update the from connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                mariaDBConnectionGroup.getParentID(), MariaDBConstants.CONNECTION_GROUP_UPDATE);
        
        // Verify permission to use the to connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (toConnectionGroupID, currentUser, MariaDBConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify permission to update the to connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                toConnectionGroupID, MariaDBConstants.CONNECTION_GROUP_UPDATE);

        // Verify that no connection already exists with this name.
        MariaDBConnectionGroup previousConnectionGroup =
                connectionGroupService.retrieveConnectionGroup(mariaDBConnectionGroup.getName(), 
                toConnectionGroupID, currentUser);
        if(previousConnectionGroup != null)
            throw new GuacamoleClientException("That connection group name is already in use.");
        
        // Verify that moving this connectionGroup would not cause a cycle
        Integer relativeParentID = toConnectionGroupID;
        while(relativeParentID != null) {
            if(relativeParentID == mariaDBConnectionGroup.getConnectionGroupID())
                throw new GuacamoleUnsupportedException("Connection group cycle detected.");
            
            MariaDBConnectionGroup relativeParentGroup = connectionGroupService.
                    retrieveConnectionGroup(relativeParentID, currentUser);
            
            relativeParentID = relativeParentGroup.getParentID();
        }
        
        // Update the connection
        mariaDBConnectionGroup.setParentID(toConnectionGroupID);
        connectionGroupService.updateConnectionGroup(mariaDBConnectionGroup);
    }

}

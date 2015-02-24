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

package net.sourceforge.guacamole.net.auth.mssql.service;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sourceforge.guacamole.net.auth.mssql.AuthenticatedUser;
import org.glyptodon.guacamole.GuacamoleSecurityException;
import net.sourceforge.guacamole.net.auth.mssql.MSSQLConnectionGroup;
import net.sourceforge.guacamole.net.auth.mssql.MSSQLConstants;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionGroupPermissionMapper;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionPermissionMapper;
import net.sourceforge.guacamole.net.auth.mssql.dao.SystemPermissionMapper;
import net.sourceforge.guacamole.net.auth.mssql.dao.UserPermissionMapper;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionGroupPermissionExample;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionGroupPermissionKey;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionPermissionExample;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionPermissionExample.Criteria;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionPermissionKey;
import net.sourceforge.guacamole.net.auth.mssql.model.SystemPermissionExample;
import net.sourceforge.guacamole.net.auth.mssql.model.SystemPermissionKey;
import net.sourceforge.guacamole.net.auth.mssql.model.UserPermissionExample;
import net.sourceforge.guacamole.net.auth.mssql.model.UserPermissionKey;
import org.glyptodon.guacamole.net.auth.permission.ConnectionGroupPermission;
import org.glyptodon.guacamole.net.auth.permission.ConnectionPermission;
import org.glyptodon.guacamole.net.auth.permission.Permission;
import org.glyptodon.guacamole.net.auth.permission.SystemPermission;
import org.glyptodon.guacamole.net.auth.permission.UserPermission;

/**
 * A service to retrieve information about what objects a user has permission to.
 * @author James Muehlner
 */
public class PermissionCheckService {

    /**
     * Service for accessing users.
     */
    @Inject
    private UserService userService;

    /**
     * Service for accessing connections.
     */
    @Inject
    private ConnectionService connectionService;

    /**
     * Service for accessing connection groups.
     */
    @Inject
    private ConnectionGroupService connectionGroupService;

    /**
     * DAO for accessing permissions related to users.
     */
    @Inject
    private UserPermissionMapper userPermissionDAO;

    /**
     * DAO for accessing permissions related to connections.
     */
    @Inject
    private ConnectionPermissionMapper connectionPermissionDAO;
    
    /**
     * DAO for accessing permissions related to connection groups.
     */
    @Inject
    private ConnectionGroupPermissionMapper connectionGroupPermissionDAO;

    /**
     * DAO for accessing permissions related to the system as a whole.
     */
    @Inject
    private SystemPermissionMapper systemPermissionDAO;

    /**
     * Verifies that the user has the specified access to the given other
     * user. If permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param affectedUserID
     *     The user that would be affected by the operation if permission is
     *     granted.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @throws GuacamoleSecurityException
     *     If the specified permission is not granted.
     */
    public void verifyUserAccess(AuthenticatedUser currentUser, int affectedUserID,
            String permissionType) throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkUserAccess(currentUser, affectedUserID, permissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }

    /**
     * Verifies that the user has the specified access to the given connection.
     * If permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param affectedConnectionID
     *     The connection that would be affected by the operation if permission
     *     is granted.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @throws GuacamoleSecurityException
     *     If the specified permission is not granted.
     */
    public void verifyConnectionAccess(AuthenticatedUser currentUser,
            int affectedConnectionID, String permissionType) throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkConnectionAccess(currentUser, affectedConnectionID, permissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }

    /**
     * Verifies that the user has the specified access to the given connection group.
     * If permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param affectedConnectionGroupID
     *     The connection group that would be affected by the operation if
     *     permission is granted.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @throws GuacamoleSecurityException
     *     If the specified permission is not granted.
     */
    public void verifyConnectionGroupAccess(AuthenticatedUser currentUser,
            Integer affectedConnectionGroupID, String permissionType) throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkConnectionGroupAccess(currentUser, affectedConnectionGroupID, permissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }
    
    /**
     * Verifies that the user has the specified access to the system. If
     * permission is denied, a GuacamoleSecurityException is thrown.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param systemPermissionType
     *     The type of permission to check for.
     *
     * @throws GuacamoleSecurityException
     *     If the specified permission is not granted.
     */
    public void verifySystemAccess(AuthenticatedUser currentUser, String systemPermissionType)
            throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkSystemAccess(currentUser, systemPermissionType))
            throw new GuacamoleSecurityException("Permission denied.");

    }

    /**
     * Checks whether a user has the specified type of access to the affected
     * user.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param affectedUserID
     *     The user that would be affected by the operation if permission is
     *     granted.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     true if the specified permission is granted, false otherwise.
     */
    public boolean checkUserAccess(AuthenticatedUser currentUser,
            Integer affectedUserID, String permissionType) {

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(currentUser))
            return true;

        // Check existence of requested permission
        UserPermissionExample example = new UserPermissionExample();
        example.createCriteria().andUser_idEqualTo(currentUser.getUserID()).andAffected_user_idEqualTo(affectedUserID).andPermissionEqualTo(permissionType);
        return userPermissionDAO.countByExample(example) > 0;

    }

    /**
     * Checks whether a user has the specified type of access to the affected
     * connection.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param affectedConnectionID
     *     The connection that would be affected by the operation if permission
     *     is granted.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     true if the specified permission is granted, false otherwise.
     */
    public boolean checkConnectionAccess(AuthenticatedUser currentUser,
            Integer affectedConnectionID, String permissionType) {

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(currentUser))
            return true;

        // Check existence of requested permission
        ConnectionPermissionExample example = new ConnectionPermissionExample();
        example.createCriteria().andUser_idEqualTo(currentUser.getUserID()).andConnection_idEqualTo(affectedConnectionID).andPermissionEqualTo(permissionType);
        return connectionPermissionDAO.countByExample(example) > 0;

    }

    /**
     * Checks whether a user has the specified type of access to the affected
     * connection group.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param affectedConnectionGroupID
     *     The connection group that would be affected by the operation if
     *     permission is granted.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     true if the specified permission is granted, false otherwise.
     */
    public boolean checkConnectionGroupAccess(AuthenticatedUser currentUser,
            Integer affectedConnectionGroupID, String permissionType) {

        // All users have implicit permission to read and update the root connection group
        if(affectedConnectionGroupID == null && 
                MSSQLConstants.CONNECTION_GROUP_READ.equals(permissionType) ||
                MSSQLConstants.CONNECTION_GROUP_UPDATE.equals(permissionType))
            return true;
        
        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(currentUser))
            return true;

        // Check existence of requested permission
        ConnectionGroupPermissionExample example = new ConnectionGroupPermissionExample();
        example.createCriteria().andUser_idEqualTo(currentUser.getUserID()).andConnection_group_idEqualTo(affectedConnectionGroupID).andPermissionEqualTo(permissionType);
        return connectionGroupPermissionDAO.countByExample(example) > 0;

    }

    /**
     * Checks whether a user has the specified type of access to the system.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param systemPermissionType
     *     The type of permission to check for.
     *
     * @return
     *     true if the specified permission is granted, false otherwise.
     */
    private boolean checkSystemAccess(AuthenticatedUser currentUser, String systemPermissionType) {

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(currentUser))
            return true;

        // Check existence of requested permission
        SystemPermissionExample example = new SystemPermissionExample();
        example.createCriteria().andUser_idEqualTo(currentUser.getUserID()).andPermissionEqualTo(systemPermissionType);
        return systemPermissionDAO.countByExample(example) > 0;

    }

    /**
     * Checks whether a user has system administrator access to the system.
     *
     * @param currentUser
     *     The user to check.
     *
     * @return
     *     true if the system administrator access exists, false otherwise.
     */
    private boolean checkSystemAdministratorAccess(AuthenticatedUser currentUser) {

        // Check existence of system administrator permission
        SystemPermissionExample example = new SystemPermissionExample();
        example.createCriteria().andUser_idEqualTo(currentUser.getUserID())
                .andPermissionEqualTo(MSSQLConstants.SYSTEM_ADMINISTER);
        return systemPermissionDAO.countByExample(example) > 0;
    }
    
    /**
     * Verifies that the specified group can be used for organization 
     * by the given user.
     * 
     * @param connectionGroupID
     *     The ID of the affected ConnectionGroup.
     *
     * @param currentUser
     *     The user to check.
     * 
     * @param type
     *     The desired usage.
     *
     * @throws GuacamoleSecurityException
     *     If the connection group cannot be used for organization.
     */
    public void verifyConnectionGroupUsageAccess(Integer connectionGroupID, 
            AuthenticatedUser currentUser, String type) throws GuacamoleSecurityException {

        // If permission does not exist, throw exception
        if(!checkConnectionGroupUsageAccess(connectionGroupID, currentUser, type))
            throw new GuacamoleSecurityException("Permission denied.");

    }
    
    /**
     * Check whether a user can use connectionGroup for the given usage.
     *
     * @param connectionGroupID
     *     The ID of the affected connection group.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param usage
     *     The desired usage.
     *
     * @return
     *     true if the user can use the connection group for the given usage.
     */
    private boolean checkConnectionGroupUsageAccess(
            Integer connectionGroupID, AuthenticatedUser currentUser, String usage) {
        
        // The root level connection group can only be used for organization
        if(connectionGroupID == null)
            return MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL.equals(usage);

        // A system administrator has full access to everything.
        if(checkSystemAdministratorAccess(currentUser))
            return true;
        
        // A connection group administrator can use the group either way.
        if(checkConnectionGroupAccess(currentUser, connectionGroupID,
                MSSQLConstants.CONNECTION_GROUP_ADMINISTER))
            return true;
        
        // Query the connection group
        MSSQLConnectionGroup connectionGroup = connectionGroupService.
                retrieveConnectionGroup(connectionGroupID, currentUser);
        
        // If the connection group is not found, it cannot be used.
        if(connectionGroup == null)
            return false;

        // Verify that the desired usage matches the type.
        return MSSQLConstants.getConnectionGroupTypeConstant(
                connectionGroup.getType()).equals(usage);
        
    }

    /**
     * Find the list of the IDs of all users a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     A list of all user IDs this user has the specified access to.
     */
    public List<Integer> retrieveUserIDs(AuthenticatedUser currentUser, String permissionType) {

        // A system administrator has access to all users.
        if(checkSystemAdministratorAccess(currentUser))
            return userService.getAllUserIDs();

        // Query all user permissions for the given user and permission type
        UserPermissionExample example = new UserPermissionExample();
        example.createCriteria().andUser_idEqualTo(currentUser.getUserID()).andPermissionEqualTo(permissionType);
        example.setDistinct(true);
        List<UserPermissionKey> userPermissions =
                userPermissionDAO.selectByExample(example);

        // Convert result into list of IDs
        List<Integer> currentUsers = new ArrayList<Integer>(userPermissions.size());
        for(UserPermissionKey permission : userPermissions)
            currentUsers.add(permission.getAffected_user_id());

        return currentUsers;

    }

    /**
     * Find the list of the IDs of all connections a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     A list of all connection IDs this user has the specified access to.
     */
    public List<Integer> retrieveConnectionIDs(AuthenticatedUser currentUser,
            String permissionType) {

        return retrieveConnectionIDs(currentUser, null, permissionType, false);

    }

    /**
     * Find the list of the IDs of all connections a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param parentID
     *     The parent connection group.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     A list of all connection IDs this user has the specified access to.
     */
    public List<Integer> retrieveConnectionIDs(AuthenticatedUser currentUser, Integer parentID,
            String permissionType) {

        return retrieveConnectionIDs(currentUser, parentID, permissionType, true);

    }

    /**
     * Find the list of the IDs of all connections a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param parentID
     *     The parent connection group.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @param checkParentID
     *     Whether the parentID should be checked or not.
     * 
     * @return
     *     A list of all connection IDs this user has the specified access to.
     */
    private List<Integer> retrieveConnectionIDs(AuthenticatedUser currentUser, Integer parentID,
            String permissionType, boolean checkParentID) {

        // A system administrator has access to all connections.
        if(checkSystemAdministratorAccess(currentUser)) {
            if(checkParentID)
                return connectionService.getAllConnectionIDs(parentID);
            else
                return connectionService.getAllConnectionIDs();
        }

        // Query all connection permissions for the given user and permission type
        ConnectionPermissionExample example = new ConnectionPermissionExample();
        Criteria criteria = example.createCriteria().andUser_idEqualTo(currentUser.getUserID())
                .andPermissionEqualTo(permissionType);
        
        // Ensure that the connections are all under the parent ID, if needed
        if(checkParentID) {
            // Get the IDs of all connections in the connection group
            List<Integer> allConnectionIDs = connectionService.getAllConnectionIDs(parentID);
            
            if(allConnectionIDs.isEmpty())
                return Collections.EMPTY_LIST;
            
            criteria.andConnection_idIn(allConnectionIDs);
        }
                                              
        example.setDistinct(true);
        List<ConnectionPermissionKey> connectionPermissions =
                connectionPermissionDAO.selectByExample(example);

        // Convert result into list of IDs
        List<Integer> connectionIDs = new ArrayList<Integer>(connectionPermissions.size());
        for(ConnectionPermissionKey permission : connectionPermissions)
            connectionIDs.add(permission.getConnection_id());

        return connectionIDs;

    }

    /**
     * Find the list of the IDs of all connection groups a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     A list of all connection group IDs this user has the specified
     *     access to.
     */
    public List<Integer> retrieveConnectionGroupIDs(AuthenticatedUser currentUser,
            String permissionType) {

        return retrieveConnectionGroupIDs(currentUser, null, permissionType, false);

    }

    /**
     * Find the list of the IDs of all connection groups a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     *
     * @param parentID
     *     The parent connection group.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @return
     *     A list of all connection group IDs this user has the specified
     *     access to.
     */
    public List<Integer> retrieveConnectionGroupIDs(AuthenticatedUser currentUser, Integer parentID,
            String permissionType) {

        return retrieveConnectionGroupIDs(currentUser, parentID, permissionType, true);

    }

    /**
     * Find the list of the IDs of all connection groups a user has permission to.
     * The access type is defined by permissionType.
     *
     * @param currentUser
     *     The user to check.
     * 
     * @param parentID
     *     The parent connection group.
     *
     * @param permissionType
     *     The type of permission to check for.
     *
     * @param checkParentID
     *     Whether the parentID should be checked or not.
     *
     * @return
     *     A list of all connection group IDs this user has the specified
     *     access to.
     */
    private List<Integer> retrieveConnectionGroupIDs(AuthenticatedUser currentUser, Integer parentID,
            String permissionType, boolean checkParentID) {

        // A system administrator has access to all connectionGroups .
        if(checkSystemAdministratorAccess(currentUser)) {
            if(checkParentID)
                return connectionGroupService.getAllConnectionGroupIDs(parentID);
            else
                return connectionGroupService.getAllConnectionGroupIDs();
        }

        // Query all connection permissions for the given user and permission type
        ConnectionGroupPermissionExample example = new ConnectionGroupPermissionExample();
        ConnectionGroupPermissionExample.Criteria criteria = 
                example.createCriteria().andUser_idEqualTo(currentUser.getUserID())
                .andPermissionEqualTo(permissionType);
        
        // Ensure that the connection groups are all under the parent ID, if needed
        if(checkParentID) {
            // Get the IDs of all connection groups in the connection group
            List<Integer> allConnectionGroupIDs = connectionGroupService
                    .getAllConnectionGroupIDs(parentID);
            
            if(allConnectionGroupIDs.isEmpty())
                return Collections.EMPTY_LIST;
            
            criteria.andConnection_group_idIn(allConnectionGroupIDs);
        }
                                              
        example.setDistinct(true);
        List<ConnectionGroupPermissionKey> connectionGroupPermissions =
                connectionGroupPermissionDAO.selectByExample(example);

        // Convert result into list of IDs
        List<Integer> connectionGroupIDs = new ArrayList<Integer>(connectionGroupPermissions.size());
        for(ConnectionGroupPermissionKey permission : connectionGroupPermissions)
            connectionGroupIDs.add(permission.getConnection_group_id());
        
        // All users have implicit access to read and update the root group
        if(MSSQLConstants.CONNECTION_GROUP_READ.equals(permissionType)
                && MSSQLConstants.CONNECTION_GROUP_UPDATE.equals(permissionType)
                && !checkParentID)
            connectionGroupIDs.add(null);

        return connectionGroupIDs;

    }

    /**
     * Retrieve all existing usernames that the given user has permission to
     * perform the given operation upon.
     *
     * @param currentUser
     *     The user whose permissions should be checked.
     *
     * @param permissionType
     *     The permission to check.
     *
     * @return
     *     A set of all usernames for which the given user has the given
     *     permission.
     */
    public Set<String> retrieveUsernames(AuthenticatedUser currentUser, String permissionType) {

        // A system administrator has access to all users.
        if(checkSystemAdministratorAccess(currentUser))
            return userService.getAllUsernames();

        // List of all user IDs for which this user has read access
        List<Integer> currentUsers =
                retrieveUserIDs(currentUser, MSSQLConstants.USER_READ);

        // Query all associated users
        return userService.translateUsernames(currentUsers).keySet();

    }

    /**
     * Retrieve all existing connection identifiers that the given user has 
     * permission to perform the given operation upon.
     *
     * @param currentUser
     *     The user whose permissions should be checked.
     *
     * @param permissionType
     *     The permission to check.
     *
     * @param parentID
     *     The parent connection group.
     *
     * @return
     *     A set of all connection identifiers for which the given user has the
     *     given permission.
     */
    public Set<String> retrieveConnectionIdentifiers(AuthenticatedUser currentUser, Integer parentID,
            String permissionType) {

        // A system administrator has access to all connections.
        if(checkSystemAdministratorAccess(currentUser))
            return connectionService.getAllConnectionIdentifiers(parentID);

        // List of all connection IDs for which this user has access
        List<Integer> connectionIDs =
                retrieveConnectionIDs(currentUser, parentID, permissionType);
        
        // Unique Identifiers for MSSQLConnections are the database IDs
        Set<String> connectionIdentifiers = new HashSet<String>();
        
        for(Integer connectionID : connectionIDs)
            connectionIdentifiers.add(Integer.toString(connectionID));

        return connectionIdentifiers;
    }

    /**
     * Retrieve all existing connection group identifiers that the given user 
     * has permission to perform the given operation upon.
     *
     * @param currentUser
     *     The user whose permissions should be checked.
     *
     * @param permissionType
     *     The permission to check.
     *
     * @param parentID
     *     The parent connection group.
     *
     * @return
     *     A set of all connection group identifiers for which the given user
     *     has the given permission.
     */
    public Set<String> retrieveConnectionGroupIdentifiers(AuthenticatedUser currentUser, Integer parentID,
            String permissionType) {

        // A system administrator has access to all connections.
        if(checkSystemAdministratorAccess(currentUser))
            return connectionGroupService.getAllConnectionGroupIdentifiers(parentID);

        // List of all connection group IDs for which this user has access
        List<Integer> connectionGroupIDs =
                retrieveConnectionGroupIDs(currentUser, parentID, permissionType);
        
        // Unique Identifiers for MSSQLConnectionGroups are the database IDs
        Set<String> connectionGroupIdentifiers = new HashSet<String>();
        
        for(Integer connectionGroupID : connectionGroupIDs)
            connectionGroupIdentifiers.add(Integer.toString(connectionGroupID));

        return connectionGroupIdentifiers;
    }

    /**
     * Retrieves all user permissions granted to the user having the given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all user permissions granted to the user having the
     *         given ID.
     */
    public Set<UserPermission> retrieveUserPermissions(int userID) {

        // Set of all permissions
        Set<UserPermission> permissions = new HashSet<UserPermission>();

        // Query all user permissions
        UserPermissionExample userPermissionExample = new UserPermissionExample();
        userPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<UserPermissionKey> userPermissions =
                userPermissionDAO.selectByExample(userPermissionExample);

        // Get list of affected user IDs
        List<Integer> affectedUserIDs = new ArrayList<Integer>();
        for(UserPermissionKey userPermission : userPermissions)
            affectedUserIDs.add(userPermission.getAffected_user_id());

        // Get corresponding usernames
        Map<Integer, String> affectedUsers =
                userService.retrieveUsernames(affectedUserIDs);

        // Add user permissions
        for(UserPermissionKey userPermission : userPermissions) {

            // Construct permission from data
            UserPermission permission = new UserPermission(
                UserPermission.Type.valueOf(userPermission.getPermission()),
                affectedUsers.get(userPermission.getAffected_user_id())
            );

            // Add to set
            permissions.add(permission);

        }

        return permissions;

    }

    /**
     * Retrieves all connection permissions granted to the user having the
     * given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all connection permissions granted to the user having
     *         the given ID.
     */
    public Set<ConnectionPermission> retrieveConnectionPermissions(int userID) {

        // Set of all permissions
        Set<ConnectionPermission> permissions = new HashSet<ConnectionPermission>();

        // Query all connection permissions
        ConnectionPermissionExample connectionPermissionExample = new ConnectionPermissionExample();
        connectionPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<ConnectionPermissionKey> connectionPermissions =
                connectionPermissionDAO.selectByExample(connectionPermissionExample);

        // Add connection permissions
        for(ConnectionPermissionKey connectionPermission : connectionPermissions) {

            // Construct permission from data
            ConnectionPermission permission = new ConnectionPermission(
                ConnectionPermission.Type.valueOf(connectionPermission.getPermission()),
                String.valueOf(connectionPermission.getConnection_id())
            );

            // Add to set
            permissions.add(permission);

        }

        return permissions;

    }

    /**
     * Retrieves all connection group permissions granted to the user having the
     * given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all connection group permissions granted to the user having
     *         the given ID.
     */
    public Set<ConnectionGroupPermission> retrieveConnectionGroupPermissions(int userID) {

        // Set of all permissions
        Set<ConnectionGroupPermission> permissions = new HashSet<ConnectionGroupPermission>();

        // Query all connection permissions
        ConnectionGroupPermissionExample connectionGroupPermissionExample = new ConnectionGroupPermissionExample();
        connectionGroupPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<ConnectionGroupPermissionKey> connectionGroupPermissions =
                connectionGroupPermissionDAO.selectByExample(connectionGroupPermissionExample);

        // Add connection permissions
        for(ConnectionGroupPermissionKey connectionGroupPermission : connectionGroupPermissions) {

            // Construct permission from data
            ConnectionGroupPermission permission = new ConnectionGroupPermission(
                ConnectionGroupPermission.Type.valueOf(connectionGroupPermission.getPermission()),
                String.valueOf(connectionGroupPermission.getConnection_group_id())
            );

            // Add to set
            permissions.add(permission);

        }
        
        // All users have implict access to read the root connection group
        permissions.add(new ConnectionGroupPermission(
            ConnectionGroupPermission.Type.READ,
            MSSQLConstants.CONNECTION_GROUP_ROOT_IDENTIFIER
        ));
        
        // All users have implict access to update the root connection group
        permissions.add(new ConnectionGroupPermission(
            ConnectionGroupPermission.Type.UPDATE,
            MSSQLConstants.CONNECTION_GROUP_ROOT_IDENTIFIER
        ));

        return permissions;

    }

    /**
     * Retrieves all system permissions granted to the user having the
     * given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all system permissions granted to the user having the
     *         given ID.
     */
    public Set<SystemPermission> retrieveSystemPermissions(int userID) {

        // Set of all permissions
        Set<SystemPermission> permissions = new HashSet<SystemPermission>();

        // And finally, system permissions
        SystemPermissionExample systemPermissionExample = new SystemPermissionExample();
        systemPermissionExample.createCriteria().andUser_idEqualTo(userID);
        List<SystemPermissionKey> systemPermissions =
                systemPermissionDAO.selectByExample(systemPermissionExample);
        for(SystemPermissionKey systemPermission : systemPermissions) {

            // User creation permission
            if(systemPermission.getPermission().equals(MSSQLConstants.SYSTEM_USER_CREATE))
                permissions.add(new SystemPermission(SystemPermission.Type.CREATE_USER));

            // System creation permission
            else if(systemPermission.getPermission().equals(MSSQLConstants.SYSTEM_CONNECTION_CREATE))
                permissions.add(new SystemPermission(SystemPermission.Type.CREATE_CONNECTION));

            // System creation permission
            else if(systemPermission.getPermission().equals(MSSQLConstants.SYSTEM_CONNECTION_GROUP_CREATE))
                permissions.add(new SystemPermission(SystemPermission.Type.CREATE_CONNECTION_GROUP));

            // System administration permission
            else if(systemPermission.getPermission().equals(MSSQLConstants.SYSTEM_ADMINISTER))
                permissions.add(new SystemPermission(SystemPermission.Type.ADMINISTER));

        }

        return permissions;

    }

    /**
     * Retrieves all permissions granted to the user having the given ID.
     *
     * @param userID The ID of the user to retrieve permissions of.
     * @return A set of all permissions granted to the user having the given
     *         ID.
     */
    public Set<Permission> retrieveAllPermissions(int userID) {

        // Set which will contain all permissions
        Set<Permission> allPermissions = new HashSet<Permission>();

        // Add user permissions
        allPermissions.addAll(retrieveUserPermissions(userID));

        // Add connection permissions
        allPermissions.addAll(retrieveConnectionPermissions(userID));
        
        // add connection group permissions
        allPermissions.addAll(retrieveConnectionGroupPermissions(userID));

        // Add system permissions
        allPermissions.addAll(retrieveSystemPermissions(userID));

        return allPermissions;
    }

}

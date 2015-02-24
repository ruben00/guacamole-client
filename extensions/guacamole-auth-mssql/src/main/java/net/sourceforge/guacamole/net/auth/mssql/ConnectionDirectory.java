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

package net.sourceforge.guacamole.net.auth.mssql;


import com.google.inject.Inject;
import java.util.Set;
import org.glyptodon.guacamole.GuacamoleClientException;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.auth.Connection;
import org.glyptodon.guacamole.net.auth.Directory;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionParameterMapper;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionPermissionMapper;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionParameter;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionParameterExample;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionPermissionKey;
import net.sourceforge.guacamole.net.auth.mssql.service.ConnectionGroupService;
import net.sourceforge.guacamole.net.auth.mssql.service.ConnectionService;
import net.sourceforge.guacamole.net.auth.mssql.service.PermissionCheckService;
import org.glyptodon.guacamole.GuacamoleResourceNotFoundException;
import org.glyptodon.guacamole.GuacamoleUnsupportedException;
import org.glyptodon.guacamole.protocol.GuacamoleConfiguration;
import org.mybatis.guice.transactional.Transactional;

/**
 * A MSSQL-based implementation of the connection directory.
 *
 * @author James Muehlner
 */
public class ConnectionDirectory implements Directory<String, Connection>{

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
     * Service managing connections.
     */
    @Inject
    private ConnectionService connectionService;

    /**
     * Service managing connection groups.
     */
    @Inject
    private ConnectionGroupService connectionGroupService;

    /**
     * Service for manipulating connection permissions in the database.
     */
    @Inject
    private ConnectionPermissionMapper connectionPermissionDAO;

    /**
     * Service for manipulating connection parameters in the database.
     */
    @Inject
    private ConnectionParameterMapper connectionParameterDAO;

    /**
     * Set the user and parentID for this directory.
     *
     * @param currentUser
     *     The user owning this connection directory.
     *
     * @param parentID
     *     The ID of the parent connection group.
     */
    public void init(AuthenticatedUser currentUser, Integer parentID) {
        this.currentUser = currentUser;
        this.parentID = parentID;
    }

    @Transactional
    @Override
    public Connection get(String identifier) throws GuacamoleException {

        // Get connection
        MSSQLConnection connection =
                connectionService.retrieveConnection(identifier, currentUser);
        
        if(connection == null)
            return null;
        
        // Verify permission to use the parent connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (connection.getParentID(), currentUser, MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify access is granted
        permissionCheckService.verifyConnectionAccess(
                currentUser,
                connection.getConnectionID(),
                MSSQLConstants.CONNECTION_READ);

        // Return connection
        return connection;

    }

    @Transactional
    @Override
    public Set<String> getIdentifiers() throws GuacamoleException {
        
        // Verify permission to use the connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (parentID, currentUser, MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL);
        
        return permissionCheckService.retrieveConnectionIdentifiers(currentUser, 
                parentID, MSSQLConstants.CONNECTION_READ);
    }

    @Transactional
    @Override
    public void add(Connection object) throws GuacamoleException {

        String name = object.getName().trim();
        if(name.isEmpty())
            throw new GuacamoleClientException("The connection name cannot be blank.");
        
        // Verify permission to create
        permissionCheckService.verifySystemAccess(currentUser,
                MSSQLConstants.SYSTEM_CONNECTION_CREATE);
        
        // Verify permission to edit the connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser, 
                this.parentID, MSSQLConstants.CONNECTION_GROUP_UPDATE);
        
        // Verify permission to use the connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (parentID, currentUser, MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify that no connection already exists with this name.
        MSSQLConnection previousConnection =
                connectionService.retrieveConnection(name, parentID, currentUser);
        if(previousConnection != null)
            throw new GuacamoleClientException("That connection name is already in use.");

        // Create connection
        MSSQLConnection connection = connectionService.createConnection(
                name, object.getConfiguration().getProtocol(), currentUser, parentID);
        
        // Set the connection ID
        object.setIdentifier(connection.getIdentifier());

        // Add connection parameters
        createConfigurationValues(connection.getConnectionID(),
                object.getConfiguration());

        // Finally, give the current user full access to the newly created
        // connection.
        ConnectionPermissionKey newConnectionPermission = new ConnectionPermissionKey();
        newConnectionPermission.setUser_id(currentUser.getUserID());
        newConnectionPermission.setConnection_id(connection.getConnectionID());

        // Read permission
        newConnectionPermission.setPermission(MSSQLConstants.CONNECTION_READ);
        connectionPermissionDAO.insert(newConnectionPermission);

        // Update permission
        newConnectionPermission.setPermission(MSSQLConstants.CONNECTION_UPDATE);
        connectionPermissionDAO.insert(newConnectionPermission);

        // Delete permission
        newConnectionPermission.setPermission(MSSQLConstants.CONNECTION_DELETE);
        connectionPermissionDAO.insert(newConnectionPermission);

        // Administer permission
        newConnectionPermission.setPermission(MSSQLConstants.CONNECTION_ADMINISTER);
        connectionPermissionDAO.insert(newConnectionPermission);

    }

    /**
     * Inserts all parameter values from the given configuration into the
     * database, associating them with the connection having the givenID.
     *
     * @param connection_id The ID of the connection to associate all
     *                      parameters with.
     * @param config The GuacamoleConfiguration to read parameters from.
     */
    private void createConfigurationValues(int connection_id,
            GuacamoleConfiguration config) {

        // Insert new parameters for each parameter in the config
        for (String name : config.getParameterNames()) {

            // Create a ConnectionParameter based on the current parameter
            ConnectionParameter parameter = new ConnectionParameter();
            parameter.setConnection_id(connection_id);
            parameter.setParameter_name(name);
            parameter.setParameter_value(config.getParameter(name));

            // Insert connection parameter
            connectionParameterDAO.insert(parameter);
        }

    }

    @Transactional
    @Override
    public void update(Connection object) throws GuacamoleException {

        // If connection not actually from this auth provider, we can't handle
        // the update
        if (!(object instanceof MSSQLConnection))
            throw new GuacamoleUnsupportedException("Connection not from database.");

        MSSQLConnection msSQLConnection = (MSSQLConnection) object;

        // Verify permission to update
        permissionCheckService.verifyConnectionAccess(currentUser,
                msSQLConnection.getConnectionID(),
                MSSQLConstants.CONNECTION_UPDATE);

        // Perform update
        connectionService.updateConnection(msSQLConnection);

        // Delete old connection parameters
        ConnectionParameterExample parameterExample = new ConnectionParameterExample();
        parameterExample.createCriteria().andConnection_idEqualTo(msSQLConnection.getConnectionID());
        connectionParameterDAO.deleteByExample(parameterExample);

        // Add connection parameters
        createConfigurationValues(msSQLConnection.getConnectionID(),
                object.getConfiguration());

    }

    @Transactional
    @Override
    public void remove(String identifier) throws GuacamoleException {

        // Get connection
        MSSQLConnection msSQLConnection =
                connectionService.retrieveConnection(identifier, currentUser);
        
        if(msSQLConnection == null)
            throw new GuacamoleResourceNotFoundException("Connection not found.");
        
        // Verify permission to use the parent connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (msSQLConnection.getParentID(), currentUser, MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify permission to delete
        permissionCheckService.verifyConnectionAccess(currentUser,
                msSQLConnection.getConnectionID(),
                MSSQLConstants.CONNECTION_DELETE);

        // Delete the connection itself
        connectionService.deleteConnection(msSQLConnection.getConnectionID());

    }

    @Override
    public void move(String identifier, Directory<String, Connection> directory) 
            throws GuacamoleException {
        
        if(!(directory instanceof ConnectionDirectory))
            throw new GuacamoleUnsupportedException("Directory not from database");
        
        Integer toConnectionGroupID = ((ConnectionDirectory)directory).parentID;
        
        // Get connection
        MSSQLConnection msSQLConnection =
                connectionService.retrieveConnection(identifier, currentUser);
        
        if(msSQLConnection == null)
            throw new GuacamoleResourceNotFoundException("Connection not found.");

        // Verify permission to update the connection
        permissionCheckService.verifyConnectionAccess(currentUser,
                msSQLConnection.getConnectionID(),
                MSSQLConstants.CONNECTION_UPDATE);
        
        // Verify permission to use the from connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (msSQLConnection.getParentID(), currentUser, MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify permission to update the from connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                msSQLConnection.getParentID(), MSSQLConstants.CONNECTION_GROUP_UPDATE);
        
        // Verify permission to use the to connection group for organizational purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (toConnectionGroupID, currentUser, MSSQLConstants.CONNECTION_GROUP_ORGANIZATIONAL);

        // Verify permission to update the to connection group
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                toConnectionGroupID, MSSQLConstants.CONNECTION_GROUP_UPDATE);

        // Verify that no connection already exists with this name.
        MSSQLConnection previousConnection =
                connectionService.retrieveConnection(msSQLConnection.getName(), 
                toConnectionGroupID, currentUser);
        if(previousConnection != null)
            throw new GuacamoleClientException("That connection name is already in use.");
        
        // Update the connection
        msSQLConnection.setParentID(toConnectionGroupID);
        connectionService.updateConnection(msSQLConnection);
    }

}

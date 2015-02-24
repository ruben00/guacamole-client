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
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.GuacamoleSocket;
import org.glyptodon.guacamole.net.InetGuacamoleSocket;
import org.glyptodon.guacamole.net.SSLGuacamoleSocket;
import net.sourceforge.guacamole.net.auth.mssql.ActiveConnectionMap;
import net.sourceforge.guacamole.net.auth.mssql.AuthenticatedUser;
import net.sourceforge.guacamole.net.auth.mssql.MSSQLConnection;
import net.sourceforge.guacamole.net.auth.mssql.MSSQLConnectionRecord;
import net.sourceforge.guacamole.net.auth.mssql.MSSQLGuacamoleSocket;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionHistoryMapper;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionMapper;
import net.sourceforge.guacamole.net.auth.mssql.dao.ConnectionParameterMapper;
import net.sourceforge.guacamole.net.auth.mssql.model.Connection;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionExample;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionExample.Criteria;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionHistory;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionHistoryExample;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionParameter;
import net.sourceforge.guacamole.net.auth.mssql.model.ConnectionParameterExample;
import net.sourceforge.guacamole.net.auth.mssql.properties.MSSQLGuacamoleProperties;
import org.glyptodon.guacamole.properties.GuacamoleProperties;
import org.glyptodon.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.glyptodon.guacamole.protocol.GuacamoleClientInformation;
import org.glyptodon.guacamole.protocol.GuacamoleConfiguration;
import org.apache.ibatis.session.RowBounds;
import org.glyptodon.guacamole.GuacamoleClientTooManyException;
import org.glyptodon.guacamole.GuacamoleResourceConflictException;
import org.glyptodon.guacamole.token.StandardTokens;
import org.glyptodon.guacamole.token.TokenFilter;

/**
 * Service which provides convenience methods for creating, retrieving, and
 * manipulating connections.
 *
 * @author Michael Jumper, James Muehlner
 */
public class ConnectionService {

    /**
     * DAO for accessing connections.
     */
    @Inject
    private ConnectionMapper connectionDAO;

    /**
     * DAO for accessing connection parameters.
     */
    @Inject
    private ConnectionParameterMapper connectionParameterDAO;

    /**
     * DAO for accessing connection history.
     */
    @Inject
    private ConnectionHistoryMapper connectionHistoryDAO;

    /**
     * Provider which creates MSSQLConnections.
     */
    @Inject
    private Provider<MSSQLConnection> msSQLConnectionProvider;

    /**
     * Provider which creates MSSQLGuacamoleSockets.
     */
    @Inject
    private Provider<MSSQLGuacamoleSocket> msSQLGuacamoleSocketProvider;

    /**
     * Map of all currently active connections.
     */
    @Inject
    private ActiveConnectionMap activeConnectionMap;

    /**
     * Service managing users.
     */
    @Inject
    private UserService userService;

    /**
     * Retrieves the connection having the given name from the database.
     *
     * @param name
     *     The name of the connection to return.
     *
     * @param parentID
     *     The ID of the parent connection group.
     *
     * @param currentUser 
     *     The user who queried this connection.
     *
     * @return
     *     The connection having the given name, or null if no such
     *     connection could be found.
     */
    public MSSQLConnection retrieveConnection(String name, Integer parentID,
            AuthenticatedUser currentUser) {

        // Create criteria
        ConnectionExample example = new ConnectionExample();
        Criteria criteria = example.createCriteria().andConnection_nameEqualTo(name);
        if(parentID != null)
            criteria.andParent_idEqualTo(parentID);
        else
            criteria.andParent_idIsNull();
        
        // Query connection by name and parentID
        List<Connection> connections =
                connectionDAO.selectByExample(example);

        // If no connection found, return null
        if(connections.isEmpty())
            return null;

        // Otherwise, return found connection
        return toMSSQLConnection(connections.get(0), currentUser);

    }

    /**
     * Retrieves the connection having the given unique identifier 
     * from the database.
     *
     * @param uniqueIdentifier
     *     The unique identifier of the connection to retrieve.
     * 
     * @param currentUser
     *     The user who queried this connection.
     *
     * @return
     *     The connection having the given unique identifier, or null if no
     *     such connection was found.
     */
    public MSSQLConnection retrieveConnection(String uniqueIdentifier, AuthenticatedUser currentUser) {

        // The unique identifier for a MSSQLConnection is the database ID
        int connectionID;
        try {
            connectionID = Integer.parseInt(uniqueIdentifier);
        } catch(NumberFormatException e) {
            // Invalid number means it can't be a DB record; not found
            return null;
        }
        
        return retrieveConnection(connectionID, currentUser);
    }

    /**
     * Retrieves the connection having the given ID from the database.
     *
     * @param id
     *     The ID of the connection to retrieve.
     *
     * @param currentUser
     *     The user who queried this connection.
     *
     * @return
     *     The connection having the given ID, or null if no such connection
     *     was found.
     */
    public MSSQLConnection retrieveConnection(int id, AuthenticatedUser currentUser) {

        // Query connection by ID
        Connection connection = connectionDAO.selectByPrimaryKey(id);

        // If no connection found, return null
        if(connection == null)
            return null;

        // Otherwise, return found connection
        return toMSSQLConnection(connection, currentUser);
    }
    
    /**
     * Returns a list of the IDs of all connections with a given parent ID.
     * @param parentID The ID of the parent for all the queried connections.
     * @return a list of the IDs of all connections with a given parent ID.
     */
    public List<Integer> getAllConnectionIDs(Integer parentID) {
        
        // Create criteria
        ConnectionExample example = new ConnectionExample();
        Criteria criteria = example.createCriteria();
        
        if(parentID != null)
            criteria.andParent_idEqualTo(parentID);
        else
            criteria.andParent_idIsNull();
        
        // Query the connections
        List<Connection> connections = connectionDAO.selectByExample(example);
        
        // List of IDs of connections with the given parent
        List<Integer> connectionIDs = new ArrayList<Integer>();
        
        for(Connection connection : connections) {
            connectionIDs.add(connection.getConnection_id());
        }
        
        return connectionIDs;
    }

    /**
     * Convert the given database-retrieved Connection into a MSSQLConnection.
     * The parameters of the given connection will be read and added to the
     * MSSQLConnection in the process.
     *
     * @param connection
     *     The connection to convert.
     *
     * @param currentUser
     *     The user who queried this connection.
     *
     * @return A new MSSQLConnection containing all data associated with the
     *         specified connection.
     */
    private MSSQLConnection toMSSQLConnection(Connection connection, AuthenticatedUser currentUser) {

        // Build configuration
        GuacamoleConfiguration config = new GuacamoleConfiguration();

        // Query parameters for configuration
        ConnectionParameterExample connectionParameterExample = new ConnectionParameterExample();
        connectionParameterExample.createCriteria().andConnection_idEqualTo(connection.getConnection_id());
        List<ConnectionParameter> connectionParameters =
                connectionParameterDAO.selectByExample(connectionParameterExample);

        // Set protocol
        config.setProtocol(connection.getProtocol());

        // Set all values for all parameters
        for (ConnectionParameter parameter : connectionParameters)
            config.setParameter(parameter.getParameter_name(),
                    parameter.getParameter_value());

        // Create new MSSQLConnection from retrieved data
        MSSQLConnection msSQLConnection = msSQLConnectionProvider.get();
        msSQLConnection.init(
            connection.getConnection_id(),
            connection.getParent_id(),
            connection.getConnection_name(),
            Integer.toString(connection.getConnection_id()),
            config,
            retrieveHistory(connection.getConnection_id()),
            currentUser
        );

        return msSQLConnection;

    }

    /**
     * Retrieves the history of the connection having the given ID.
     *
     * @param connectionID The ID of the connection to retrieve the history of.
     * @return A list of MSSQLConnectionRecord documenting the history of this
     *         connection.
     */
    public List<MSSQLConnectionRecord> retrieveHistory(int connectionID) {

        // Retrieve history records relating to given connection ID
        ConnectionHistoryExample example = new ConnectionHistoryExample();
        example.createCriteria().andConnection_idEqualTo(connectionID);

        // We want to return the newest records first
        example.setOrderByClause("start_date DESC");

        // Set the maximum number of history records returned to 100
        RowBounds rowBounds = new RowBounds(0, 100);

        // Retrieve all connection history entries
        List<ConnectionHistory> connectionHistories =
                connectionHistoryDAO.selectByExampleWithRowbounds(example, rowBounds);

        // Convert history entries to connection records
        List<MSSQLConnectionRecord> connectionRecords = new ArrayList<MSSQLConnectionRecord>();
        Set<Integer> userIDSet = new HashSet<Integer>();
        for(ConnectionHistory history : connectionHistories) {
            userIDSet.add(history.getUser_id());
        }

        // Determine whether connection is currently active
        int user_count = activeConnectionMap.getCurrentUserCount(connectionID);

        // Get all the usernames for the users who are in the history
        Map<Integer, String> usernameMap = userService.retrieveUsernames(userIDSet);

        // Create the new ConnectionRecords
        for(ConnectionHistory history : connectionHistories) {

            Date startDate = history.getStart_date();
            Date endDate = history.getEnd_date();
            String username = usernameMap.get(history.getUser_id());

            // If there are active users, list the top N not-ended connections
            // as active (best guess)
            MSSQLConnectionRecord connectionRecord;
            if (user_count > 0 && endDate == null) {
                connectionRecord = new MSSQLConnectionRecord(startDate, endDate, username, true);
                user_count--;
            }

            // If no active users, or end date is recorded, connection is not
            // active.
            else
                connectionRecord = new MSSQLConnectionRecord(startDate, endDate, username, false);

            connectionRecords.add(connectionRecord);

        }

        return connectionRecords;
    }
    
    

    /**
     * Create a MSSQLGuacamoleSocket using the provided connection.
     *
     * @param connection
     *     The connection to use when connecting the socket.
     *
     * @param info
     *     The information to use when performing the connection handshake.
     *
     * @param currentUser 
     *     The user who is connecting to the socket.
     *
     * @param connectionGroupID
     *     The ID of the balancing connection group that is being connected to;
     *     null if not used.
     *
     * @return
     *     The connected socket.
     *
     * @throws GuacamoleException
     *     If an error occurs while connecting the socket.
     */
    public MSSQLGuacamoleSocket connect(MSSQLConnection connection,
            GuacamoleClientInformation info, AuthenticatedUser currentUser,
            Integer connectionGroupID)
        throws GuacamoleException {

        synchronized (activeConnectionMap) {

            // If the given connection is active, and multiple simultaneous
            // connections are not allowed, disallow connection
            if(GuacamoleProperties.getProperty(
                    MSSQLGuacamoleProperties.MSSQL_DISALLOW_SIMULTANEOUS_CONNECTIONS, false)
                    && activeConnectionMap.isActive(connection.getConnectionID()))
                throw new GuacamoleResourceConflictException("Cannot connect. This connection is in use.");
            
            if(GuacamoleProperties.getProperty(
                    MSSQLGuacamoleProperties.MSSQL_DISALLOW_DUPLICATE_CONNECTIONS, true)
                    && activeConnectionMap.isConnectionUserActive(connection.getConnectionID(), currentUser.getUserID()))
                throw new GuacamoleClientTooManyException
                        ("Cannot connect. Connection already in use by this user.");

            // Get guacd connection information
            String host = GuacamoleProperties.getRequiredProperty(GuacamoleProperties.GUACD_HOSTNAME);
            int port = GuacamoleProperties.getRequiredProperty(GuacamoleProperties.GUACD_PORT);

            // Build token filter containing credential tokens
            TokenFilter tokenFilter = new TokenFilter();
            StandardTokens.addStandardTokens(tokenFilter, currentUser.getCredentials());

            // Filter the configuration
            GuacamoleConfiguration config = new GuacamoleConfiguration(connection.getConfiguration());
            tokenFilter.filterValues(config.getParameters());
            
            // Get socket
            GuacamoleSocket socket;
            if (GuacamoleProperties.getProperty(GuacamoleProperties.GUACD_SSL, false))
                socket = new ConfiguredGuacamoleSocket(
                    new SSLGuacamoleSocket(host, port),
                    config, info
                );
            else
                socket = new ConfiguredGuacamoleSocket(
                    new InetGuacamoleSocket(host, port),
                    config, info
                );

            // Mark this connection as active
            int historyID = activeConnectionMap.openConnection(connection.getConnectionID(), 
                    currentUser.getUserID(), connectionGroupID);

                // Return new MSSQLGuacamoleSocket
            MSSQLGuacamoleSocket msSQLGuacamoleSocket = msSQLGuacamoleSocketProvider.get();
            msSQLGuacamoleSocket.init(socket, historyID, connectionGroupID);
                
            return msSQLGuacamoleSocket;

        }

    }

    /**
     * Creates a new connection having the given name and protocol.
     *
     * @param name
     *     The name to assign to the new connection.
     *
     * @param protocol
     *     The protocol to assign to the new connection.
     *
     * @param currentUser
     *     The user who created this connection.
     *
     * @param parentID
     *     The ID of the parent connection group.
     *
     * @return
     *     A new MSSQLConnection containing the data of the newly created
     *     connection.
     */
    public MSSQLConnection createConnection(String name, String protocol,
            AuthenticatedUser currentUser, Integer parentID) {

        // Initialize database connection
        Connection connection = new Connection();
        connection.setConnection_name(name);
        connection.setProtocol(protocol);
        connection.setParent_id(parentID);

        // Create connection
        connectionDAO.insert(connection);
        return toMSSQLConnection(connection, currentUser);

    }

    /**
     * Deletes the connection having the given ID from the database.
     * @param id The ID of the connection to delete.
     */
    public void deleteConnection(int id) {
        connectionDAO.deleteByPrimaryKey(id);
    }

    /**
     * Updates the connection in the database corresponding to the given
     * MSSQLConnection.
     *
     * @param msSQLConnection The MSSQLConnection to update (save) to the
     *                        database. This connection must already exist.
     */
    public void updateConnection(MSSQLConnection msSQLConnection) {

        // Populate connection
        Connection connection = new Connection();
        connection.setConnection_id(msSQLConnection.getConnectionID());
        connection.setParent_id(msSQLConnection.getParentID());
        connection.setConnection_name(msSQLConnection.getName());
        connection.setProtocol(msSQLConnection.getConfiguration().getProtocol());

        // Update the connection in the database
        connectionDAO.updateByPrimaryKey(connection);

    }

    /**
     * Get the identifiers of all the connections defined in the system 
     * with a certain parentID.
     *
     * @return A Set of identifiers of all the connections defined in the system
     * with the given parentID.
     */
    public Set<String> getAllConnectionIdentifiers(Integer parentID) {

        // Set of all present connection identifiers
        Set<String> identifiers = new HashSet<String>();
        
        // Set up Criteria
        ConnectionExample example = new ConnectionExample();
        Criteria criteria = example.createCriteria();
        if(parentID != null)
            criteria.andParent_idEqualTo(parentID);
        else
            criteria.andParent_idIsNull();

        // Query connection identifiers
        List<Connection> connections =
                connectionDAO.selectByExample(example);
        for (Connection connection : connections)
            identifiers.add(String.valueOf(connection.getConnection_id()));

        return identifiers;

    }

    /**
     * Get the connection IDs of all the connections defined in the system 
     * with a certain parent connection group.
     *
     * @return A list of connection IDs of all the connections defined in the system.
     */
    public List<Integer> getAllConnectionIDs() {

        // Set of all present connection IDs
        List<Integer> connectionIDs = new ArrayList<Integer>();

        // Create the criteria
        ConnectionExample example = new ConnectionExample();
        
        // Query the connections
        List<Connection> connections =
                connectionDAO.selectByExample(example);
        for (Connection connection : connections)
            connectionIDs.add(connection.getConnection_id());

        return connectionIDs;

    }

}

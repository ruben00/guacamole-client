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
import com.google.inject.Provider;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.GuacamoleSocket;
import org.glyptodon.guacamole.net.auth.AbstractConnectionGroup;
import org.glyptodon.guacamole.net.auth.Connection;
import org.glyptodon.guacamole.net.auth.ConnectionGroup;
import org.glyptodon.guacamole.net.auth.Directory;
import net.sourceforge.guacamole.net.auth.mssql.service.ConnectionGroupService;
import net.sourceforge.guacamole.net.auth.mssql.service.PermissionCheckService;
import org.glyptodon.guacamole.protocol.GuacamoleClientInformation;

/**
 * A MSSQL based implementation of the ConnectionGroup object.
 * @author James Muehlner
 */
public class MSSQLConnectionGroup extends AbstractConnectionGroup {

    /**
     * The ID associated with this connection group in the database.
     */
    private Integer connectionGroupID;

    /**
     * The ID of the parent connection group for this connection group.
     */
    private Integer parentID;

    /**
     * The user who queried or created this connection group.
     */
    private AuthenticatedUser currentUser;
    
    /**
     * A Directory of connections that have this connection group as a parent.
     */
    private ConnectionDirectory connectionDirectory = null;
    
    /**
     * A Directory of connection groups that have this connection group as a parent.
     */
    private ConnectionGroupDirectory connectionGroupDirectory = null;

    /**
     * Service managing connection groups.
     */
    @Inject
    private ConnectionGroupService connectionGroupService;

    /**
     * Service for checking permissions.
     */
    @Inject
    private PermissionCheckService permissionCheckService;
    
    /**
     * Service for creating new ConnectionDirectory objects.
     */
    @Inject Provider<ConnectionDirectory> connectionDirectoryProvider;
    
    /**
     * Service for creating new ConnectionGroupDirectory objects.
     */
    @Inject Provider<ConnectionGroupDirectory> connectionGroupDirectoryProvider;

    /**
     * Create a default, empty connection group.
     */
    public MSSQLConnectionGroup() {
    }

    /**
     * Get the ID of the corresponding connection group record.
     * @return The ID of the corresponding connection group, if any.
     */
    public Integer getConnectionGroupID() {
        return connectionGroupID;
    }

    /**
     * Sets the ID of the corresponding connection group record.
     * @param connectionGroupID The ID to assign to this connection group.
     */
    public void setConnectionID(Integer connectionGroupID) {
        this.connectionGroupID = connectionGroupID;
    }

    /**
     * Get the ID of the parent connection group for this connection group, if any.
     * @return The ID of the parent connection group for this connection group, if any.
     */
    public Integer getParentID() {
        return parentID;
    }

    /**
     * Sets the ID of the parent connection group for this connection group.
     * @param parentID The ID of the parent connection group for this connection group.
     */
    public void setParentID(Integer parentID) {
        this.parentID = parentID;

        // Translate to string identifier
        if (parentID != null)
            this.setParentIdentifier(String.valueOf(parentID));
        else
            this.setParentIdentifier(MSSQLConstants.CONNECTION_GROUP_ROOT_IDENTIFIER);

    }

    /**
     * Initialize from explicit values.
     *
     * @param connectionGroupID
     *     The ID of the associated database record, if any.
     *
     * @param parentID
     *     The ID of the parent connection group for this connection group, if
     *     any.
     *
     * @param name
     *     The name of this connection group.
     *
     * @param identifier
     *     The unique identifier associated with this connection group.
     *
     * @param type
     *     The type of this connection group.
     *
     * @param currentUser
     *     The user who queried this connection.
     */
    public void init(Integer connectionGroupID, Integer parentID, String name, 
            String identifier, ConnectionGroup.Type type, AuthenticatedUser currentUser) {
        this.connectionGroupID = connectionGroupID;
        this.setParentID(parentID);
        setName(name);
        setIdentifier(identifier);
        setType(type);
        this.currentUser = currentUser;
        
        connectionDirectory = connectionDirectoryProvider.get();
        connectionDirectory.init(currentUser, connectionGroupID);
        
        connectionGroupDirectory = connectionGroupDirectoryProvider.get();
        connectionGroupDirectory.init(currentUser, connectionGroupID);
    }

    @Override
    public GuacamoleSocket connect(GuacamoleClientInformation info) throws GuacamoleException {
        
        // Verify permission to use the connection group for balancing purposes
        permissionCheckService.verifyConnectionGroupUsageAccess
                (this.connectionGroupID, currentUser, MSSQLConstants.CONNECTION_GROUP_BALANCING);

        // Verify permission to delete
        permissionCheckService.verifyConnectionGroupAccess(currentUser,
                this.connectionGroupID,
                MSSQLConstants.CONNECTION_GROUP_READ);
        
        return connectionGroupService.connect(this, info, currentUser);
    }
    
    @Override
    public Directory<String, Connection> getConnectionDirectory() throws GuacamoleException {
        return connectionDirectory;
    }

    @Override
    public Directory<String, ConnectionGroup> getConnectionGroupDirectory() throws GuacamoleException {
        return connectionGroupDirectory;
    }

}

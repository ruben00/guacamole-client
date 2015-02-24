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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.GuacamoleSocket;
import org.glyptodon.guacamole.net.auth.AbstractConnection;
import org.glyptodon.guacamole.net.auth.ConnectionRecord;
import net.sourceforge.guacamole.net.auth.mssql.service.ConnectionService;
import org.glyptodon.guacamole.protocol.GuacamoleClientInformation;
import org.glyptodon.guacamole.protocol.GuacamoleConfiguration;

/**
 * A MSSQL based implementation of the Connection object.
 * @author James Muehlner
 */
public class MSSQLConnection extends AbstractConnection {

    /**
     * The ID associated with this connection in the database.
     */
    private Integer connectionID;

    /**
     * The ID of the parent connection group for this connection.
     */
    private Integer parentID;

    /**
     * The user who queried or created this connection.
     */
    private AuthenticatedUser currentUser;

    /**
     * History of this connection.
     */
    private List<ConnectionRecord> history = new ArrayList<ConnectionRecord>();

    /**
     * Service for managing connections.
     */
    @Inject
    private ConnectionService connectionService;

    /**
     * Create a default, empty connection.
     */
    public MSSQLConnection() {
    }

    /**
     * Get the ID of the corresponding connection record.
     * @return The ID of the corresponding connection, if any.
     */
    public Integer getConnectionID() {
        return connectionID;
    }

    /**
     * Sets the ID of the corresponding connection record.
     * @param connectionID The ID to assign to this connection.
     */
    public void setConnectionID(Integer connectionID) {
        this.connectionID = connectionID;
    }

    /**
     * Get the ID of the parent connection group for this connection, if any.
     * @return The ID of the parent connection group for this connection, if any.
     */
    public Integer getParentID() {
        return parentID;
    }

    /**
     * Sets the ID of the parent connection group for this connection.
     * @param parentID The ID of the parent connection group for this connection.
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
     * @param connectionID
     *     The ID of the associated database record, if any.
     *
     * @param parentID
     *     The ID of the parent connection group for this connection, if any.
     *
     * @param name 
     *     The human-readable name associated with this connection.
     *
     * @param identifier
     *     The unique identifier associated with this connection.
     *
     * @param config
     *     The GuacamoleConfiguration associated with this connection.
     *
     * @param history
     *     All ConnectionRecords associated with this connection.
     *
     * @param currentUser 
     *     The user who queried this connection.
     */
    public void init(Integer connectionID, Integer parentID, String name, 
            String identifier, GuacamoleConfiguration config,
            List<? extends ConnectionRecord> history,
            AuthenticatedUser currentUser) {

        this.connectionID = connectionID;
        this.setParentID(parentID);
        setName(name);
        setIdentifier(identifier);
        setConfiguration(config);
        this.history.addAll(history);
        this.currentUser = currentUser;

    }

    @Override
    public GuacamoleSocket connect(GuacamoleClientInformation info) throws GuacamoleException {
        return connectionService.connect(this, info, currentUser, null);
    }

    @Override
    public List<? extends ConnectionRecord> getHistory() throws GuacamoleException {
        return Collections.unmodifiableList(history);
    }

}

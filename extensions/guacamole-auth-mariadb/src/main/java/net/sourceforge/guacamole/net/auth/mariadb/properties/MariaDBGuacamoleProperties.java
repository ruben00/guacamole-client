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

package net.sourceforge.guacamole.net.auth.mariadb.properties;

import org.glyptodon.guacamole.properties.BooleanGuacamoleProperty;
import org.glyptodon.guacamole.properties.IntegerGuacamoleProperty;
import org.glyptodon.guacamole.properties.StringGuacamoleProperty;

/**
 * Properties used by the MariaDB Authentication plugin.
 * @author James Muehlner
 */
public class MariaDBGuacamoleProperties {

    /**
     * This class should not be instantiated.
     */
    private MariaDBGuacamoleProperties() {}

    /**
     * The URL of the MariaDB server hosting the guacamole authentication tables.
     */
    public static final StringGuacamoleProperty MARIADB_HOSTNAME = new StringGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-hostname"; }

    };

    /**
     * The port of the MariaDB server hosting the guacamole authentication tables.
     */
    public static final IntegerGuacamoleProperty MARIADB_PORT = new IntegerGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-port"; }

    };

    /**
     * The name of the MariaDB database containing the guacamole authentication tables.
     */
    public static final StringGuacamoleProperty MARIADB_DATABASE = new StringGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-database"; }

    };

    /**
     * The username used to authenticate to the MariaDB database containing the guacamole authentication tables.
     */
    public static final StringGuacamoleProperty MARIADB_USERNAME = new StringGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-username"; }

    };

    /**
     * The password used to authenticate to the MariaDB database containing the guacamole authentication tables.
     */
    public static final StringGuacamoleProperty MARIADB_PASSWORD = new StringGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-password"; }

    };

    /**
     * Whether or not multiple users accessing the same connection at the same time should be disallowed.
     */
    public static final BooleanGuacamoleProperty MARIADB_DISALLOW_SIMULTANEOUS_CONNECTIONS = new BooleanGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-disallow-simultaneous-connections"; }

    };

    /**
     * Whether or not the same user accessing the same connection or connection group at the same time should be disallowed.
     */
    public static final BooleanGuacamoleProperty MARIADB_DISALLOW_DUPLICATE_CONNECTIONS = new BooleanGuacamoleProperty() {

        @Override
        public String getName() { return "mariadb-disallow-duplicate-connections"; }

    };
    
    
}

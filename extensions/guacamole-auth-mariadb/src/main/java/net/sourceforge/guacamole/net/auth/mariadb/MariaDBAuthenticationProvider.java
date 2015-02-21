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

package net.sourceforge.guacamole.net.auth.mariaDB;


import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import java.util.Properties;
import org.glyptodon.guacamole.GuacamoleException;
import org.glyptodon.guacamole.net.auth.AuthenticationProvider;
import org.glyptodon.guacamole.net.auth.Credentials;
import org.glyptodon.guacamole.net.auth.UserContext;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.ConnectionGroupMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.ConnectionGroupPermissionMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.ConnectionHistoryMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.ConnectionMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.ConnectionParameterMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.ConnectionPermissionMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.SystemPermissionMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.UserMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.dao.UserPermissionMapper;
import net.sourceforge.guacamole.net.auth.mariaDB.properties.mariaDBGuacamoleProperties;
import net.sourceforge.guacamole.net.auth.mariaDB.service.ConnectionGroupService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.ConnectionService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.PasswordEncryptionService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.PermissionCheckService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.SHA256PasswordEncryptionService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.SaltService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.SecureRandomSaltService;
import net.sourceforge.guacamole.net.auth.mariaDB.service.UserService;
import org.glyptodon.guacamole.properties.GuacamoleProperties;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.guice.MyBatisModule;
import org.mybatis.guice.datasource.builtin.PooledDataSourceProvider;
import org.mybatis.guice.datasource.helper.JdbcHelper;

/**
 * Provides a mariaDB based implementation of the AuthenticationProvider
 * functionality.
 *
 * @author James Muehlner
 */
public class mariaDBAuthenticationProvider implements AuthenticationProvider {

    /**
     * Set of all active connections.
     */
    private ActiveConnectionMap activeConnectionMap = new ActiveConnectionMap();

    /**
     * Injector which will manage the object graph of this authentication
     * provider.
     */
    private Injector injector;

    @Override
    public UserContext getUserContext(Credentials credentials) throws GuacamoleException {

        // Get user service
        UserService userService = injector.getInstance(UserService.class);

        // Get user
        mariaDBUser authenticatedUser = userService.retrieveUser(credentials);
        if (authenticatedUser != null) {
            mariaDBUserContext context = injector.getInstance(mariaDBUserContext.class);
            context.init(new AuthenticatedUser(authenticatedUser.getUserID(), credentials));
            return context;
        }

        // Otherwise, unauthorized
        return null;

    }

    /**
     * Creates a new mariaDBAuthenticationProvider that reads and writes
     * authentication data to a mariaDB database defined by properties in
     * guacamole.properties.
     *
     * @throws GuacamoleException If a required property is missing, or
     *                            an error occurs while parsing a property.
     */
    public mariaDBAuthenticationProvider() throws GuacamoleException {

        final Properties myBatisProperties = new Properties();
        final Properties driverProperties = new Properties();

        // Set the mariaDB properties for MyBatis.
        myBatisProperties.setProperty("mybatis.environment.id", "guacamole");
        myBatisProperties.setProperty("JDBC.host", GuacamoleProperties.getRequiredProperty(mariaDBGuacamoleProperties.mariaDB_HOSTNAME));
        myBatisProperties.setProperty("JDBC.port", String.valueOf(GuacamoleProperties.getRequiredProperty(mariaDBGuacamoleProperties.mariaDB_PORT)));
        myBatisProperties.setProperty("JDBC.schema", GuacamoleProperties.getRequiredProperty(mariaDBGuacamoleProperties.mariaDB_DATABASE));
        myBatisProperties.setProperty("JDBC.username", GuacamoleProperties.getRequiredProperty(mariaDBGuacamoleProperties.mariaDB_USERNAME));
        myBatisProperties.setProperty("JDBC.password", GuacamoleProperties.getRequiredProperty(mariaDBGuacamoleProperties.mariaDB_PASSWORD));
        myBatisProperties.setProperty("JDBC.autoCommit", "false");
        myBatisProperties.setProperty("mybatis.pooled.pingEnabled", "true");
        myBatisProperties.setProperty("mybatis.pooled.pingQuery", "SELECT 1");
        driverProperties.setProperty("characterEncoding","UTF-8");

        // Set up Guice injector.
        injector = Guice.createInjector(
            JdbcHelper.mariaDB,

            new Module() {
                @Override
                public void configure(Binder binder) {
                    Names.bindProperties(binder, myBatisProperties);
                    binder.bind(Properties.class)
                        .annotatedWith(Names.named("JDBC.driverProperties"))
                        .toInstance(driverProperties);
                }
            },

            new MyBatisModule() {
                @Override
                protected void initialize() {

                    // Datasource
                    bindDataSourceProviderType(PooledDataSourceProvider.class);

                    // Transaction factory
                    bindTransactionFactoryType(JdbcTransactionFactory.class);

                    // Add MyBatis mappers
                    addMapperClass(ConnectionHistoryMapper.class);
                    addMapperClass(ConnectionMapper.class);
                    addMapperClass(ConnectionGroupMapper.class);
                    addMapperClass(ConnectionGroupPermissionMapper.class);
                    addMapperClass(ConnectionParameterMapper.class);
                    addMapperClass(ConnectionPermissionMapper.class);
                    addMapperClass(SystemPermissionMapper.class);
                    addMapperClass(UserMapper.class);
                    addMapperClass(UserPermissionMapper.class);

                    // Bind interfaces
                    bind(mariaDBUserContext.class);
                    bind(UserDirectory.class);
                    bind(mariaDBUser.class);
                    bind(SaltService.class).to(SecureRandomSaltService.class);
                    bind(PasswordEncryptionService.class).to(SHA256PasswordEncryptionService.class);
                    bind(PermissionCheckService.class);
                    bind(ConnectionService.class);
                    bind(ConnectionGroupService.class);
                    bind(UserService.class);
                    bind(ActiveConnectionMap.class).toInstance(activeConnectionMap);

                }
            } // end of mybatis module

        );
    } // end of constructor

    @Override
    public UserContext updateUserContext(UserContext context,
        Credentials credentials) throws GuacamoleException {

        // No need to update the context
        return context;

    }

}

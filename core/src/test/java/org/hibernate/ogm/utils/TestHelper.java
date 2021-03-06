/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Environment;
import org.hibernate.jpa.HibernateEntityManagerFactory;
import org.hibernate.ogm.cfg.OgmConfiguration;
import org.hibernate.ogm.cfg.OgmProperties;
import org.hibernate.ogm.datastore.document.options.AssociationStorageType;
import org.hibernate.ogm.datastore.impl.AvailableDatastoreProvider;
import org.hibernate.ogm.datastore.spi.DatastoreProvider;
import org.hibernate.ogm.dialect.spi.GridDialect;
import org.hibernate.ogm.model.key.spi.EntityKey;
import org.hibernate.ogm.options.navigation.GlobalContext;
import org.hibernate.ogm.util.impl.Log;
import org.hibernate.ogm.util.impl.LoggerFactory;

import com.arjuna.ats.arjuna.coordinator.TxControl;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Emmanuel Bernard &lt;emmanuel@hibernate.org&gt;
 * @author Sanne Grinovero &lt;sanne@hibernate.org&gt;
 */
public class TestHelper {

	private static final Log log = LoggerFactory.make();
	private static final String TX_CONTROL_CLASS_NAME = "com.arjuna.ats.arjuna.coordinator.TxControl";
	private static final GridDialectType gridDialectType = determineGridDialectType();
	private static final TestableGridDialect helper = instantiate( gridDialectType.loadTestableGridDialectClass() );

	static {
		Class<?> txControlClass = loadClass( TX_CONTROL_CLASS_NAME );
		if ( txControlClass != null ) {
			// set 2 hours timeout on transactions: enough for debug, but not too high in case of CI problems.
			try {
				Method timeoutMethod = txControlClass.getMethod( "setDefaultTimeout", int.class );
				timeoutMethod.invoke( null, 60 * 60 * 2 );
			}
			catch ( NoSuchMethodException e ) {
				log.error( "Found TxControl class, but unable to set timeout" );
			}
			catch ( IllegalAccessException e ) {
				log.error( "Found TxControl class, but unable to set timeout" );
			}
			catch ( InvocationTargetException e ) {
				log.error( "Found TxControl class, but unable to set timeout" );
			}
			TxControl.setDefaultTimeout( 60 * 60 * 2 );
		}
	}

	private TestHelper() {
	}

	private static GridDialectType determineGridDialectType() {
		for ( GridDialectType gridType : GridDialectType.values() ) {
			Class<TestableGridDialect> testDialectClass = gridType.loadTestableGridDialectClass();
			if ( testDialectClass != null ) {
				return gridType;
			}
		}

		return GridDialectType.HASHMAP;
	}

	private static TestableGridDialect instantiate(Class<TestableGridDialect> testableGridDialectClass) {
		if ( testableGridDialectClass == null ) {
			return new HashMapTestHelper();
		}

		try {
			TestableGridDialect testableGridDialect = testableGridDialectClass.newInstance();
			log.debugf( "Using TestGridDialect %s", testableGridDialectClass );
			return testableGridDialect;
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
	}

	public static long getNumberOfEntities(EntityManager em) {
		return getNumberOfEntities( em.unwrap( Session.class ) );
	}

	public static GridDialectType getCurrentDialectType() {
		return gridDialectType;
	}

	public static AvailableDatastoreProvider getCurrentDatastoreProvider() {
		return DatastoreProviderHolder.INSTANCE;
	}

	public static GridDialect getCurrentGridDialect(DatastoreProvider datastoreProvider) {
		return helper.getGridDialect( datastoreProvider );
	}

	public static long getNumberOfEntities( Session session) {
		return getNumberOfEntities( session.getSessionFactory() );
	}

	public static long getNumberOfEntities(SessionFactory sessionFactory) {
		return helper.getNumberOfEntities( sessionFactory );
	}

	public static Map<String, Object> extractEntityTuple(SessionFactory sessionFactory, EntityKey key) {
		return helper.extractEntityTuple( sessionFactory, key );
	}

	public static long getNumberOfAssociations(SessionFactory sessionFactory) {
		return helper.getNumberOfAssociations( sessionFactory );
	}

	/**
	 * Returns the number of associations of the given type.
	 * <p>
	 * Optional operation which only is supported for document datastores.
	 */
	public static long getNumberOfAssociations(SessionFactory sessionFactory, AssociationStorageType type) {
		return helper.getNumberOfAssociations( sessionFactory, type );
	}

	public static boolean backendSupportsTransactions() {
		return helper.backendSupportsTransactions();
	}

	@SuppressWarnings("unchecked")
	public static <T> T get(Session session, Class<T> clazz, Serializable id) {
		return (T) session.get( clazz, id );
	}

	public static void dropSchemaAndDatabase(Session session) {
		if ( session != null ) {
			dropSchemaAndDatabase( session.getSessionFactory() );
		}
	}

	public static void dropSchemaAndDatabase(EntityManagerFactory emf) {
		dropSchemaAndDatabase( ( (HibernateEntityManagerFactory) emf ).getSessionFactory() );
	}

	public static void dropSchemaAndDatabase(SessionFactory sessionFactory) {
		// if the factory is closed, we don't have access to the service registry
		if ( sessionFactory != null && !sessionFactory.isClosed() ) {
			try {
				helper.dropSchemaAndDatabase( sessionFactory );
			}
			catch ( Exception e ) {
				log.warn( "Exception while dropping schema and database in test", e );
			}
		}
	}

	public static Map<String, String> getEnvironmentProperties() {
		//TODO hibernate.properties is ignored due to HHH-8635, thus explicitly load its properties
		Map<String, String> properties = getHibernateProperties();
		Map<String, String> environmentProperties = helper.getEnvironmentProperties();

		if (environmentProperties != null ) {
			properties.putAll( environmentProperties );
		}

		return properties;
	}

	private static Map<String, String> getHibernateProperties() {
		InputStream hibernatePropertiesStream = null;
		Map<String, String> properties = new HashMap<String, String>();

		try {
			hibernatePropertiesStream = TestHelper.class.getResourceAsStream( "/hibernate.properties" );
			Properties hibernateProperties = new Properties();
			hibernateProperties.load( hibernatePropertiesStream );

			for ( Entry<Object, Object> property : hibernateProperties.entrySet() ) {
				properties.put( property.getKey().toString(), property.getValue().toString() );
			}

			return properties;
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
		finally {
			closeQuietly( hibernatePropertiesStream );
		}
	}

	private static void closeQuietly(InputStream stream) {
		if ( stream != null ) {
			try {
				stream.close();
			}
			catch (IOException e) {
				//ignore
			}
		}
	}

	public static void checkCleanCache(SessionFactory sessionFactory) {
		assertThat( getNumberOfEntities( sessionFactory ) ).as( "Entity cache should be empty" ).isEqualTo( 0 );
		assertThat( getNumberOfAssociations( sessionFactory ) ).as( "Association cache should be empty" ).isEqualTo( 0 );
	}

	/**
	 * Provides a default {@link OgmConfiguration} for tests, using the given set of annotated entity types.
	 *
	 * @param entityTypes the entity types for which to build a configuration
	 * @return a default configuration based on the given types
	 */
	public static OgmConfiguration getDefaultTestConfiguration(Class<?>... entityTypes) {
		OgmConfiguration configuration = new OgmConfiguration();

		for ( Map.Entry<String, String> entry : TestHelper.getEnvironmentProperties().entrySet() ) {
			configuration.setProperty( entry.getKey(), entry.getValue() );
		}

		configuration.setProperty( Environment.HBM2DDL_AUTO, "none" );

		// volatile indexes for Hibernate Search (if used)
		configuration.setProperty( "hibernate.search.default.directory_provider", "ram" );
		// disable warnings about unspecified Lucene version
		configuration.setProperty( "hibernate.search.lucene_version", "LUCENE_35" );

		for ( Class<?> aClass : entityTypes ) {
			configuration.addAnnotatedClass( aClass );
		}

		return configuration;
	}

	/**
	 * Returns a {@link GlobalContext} for configuring the current datastore.
	 *
	 * @param configuration the target the configuration will be applied to
	 * @return a context object for configuring the current datastore.
	 */
	public static GlobalContext<?, ?> configureDatastore(OgmConfiguration configuration) {
		return helper.configureDatastore( configuration );
	}

	private static Class<?> loadClass(String className) {
		try {
			return Class.forName( className, true, TestHelper.class.getClassLoader() );
		}
		catch ( ClassNotFoundException e ) {
			//ignore -- try using the class loader of context first
		}
		catch ( RuntimeException e ) {
			// ignore
		}
		try {
			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			if ( contextClassLoader != null ) {
				return Class.forName( className, false, contextClassLoader );
			}
			else {
				return null;
			}
		}
		catch ( ClassNotFoundException e ) {
			return null;
		}
	}

	private static class DatastoreProviderHolder {

		private static final AvailableDatastoreProvider INSTANCE = getDatastoreProvider();

		private static AvailableDatastoreProvider getDatastoreProvider() {
			// This ignores the case where the provider is given as class or FQN; That's ok for now, can be extended if
			// needed
			String datastoreProviderProperty = new OgmConfiguration().getProperty( OgmProperties.DATASTORE_PROVIDER );
			AvailableDatastoreProvider provider = AvailableDatastoreProvider.byShortName( datastoreProviderProperty );

			if ( provider == null ) {
				throw new IllegalStateException( "Could not determine datastore provider from value: " + datastoreProviderProperty );
			}

			return provider;
		}
	}
}

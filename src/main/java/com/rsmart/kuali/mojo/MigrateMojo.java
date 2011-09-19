package com.rsmart.kuali.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.liquibase.maven.plugins.AbstractLiquibaseUpdateMojo;

import java.util.Properties;

/**
 * Migrate Liquibase changelogs
 *
 *
 * @goal migrate
 */
public class MigrateMojo extends AbstractLiquibaseUpdateMojo {
    public static final String URL_PROP       = "url";
    public static final String CLASSPATH_PROP = "classpath";
    public static final String USERNAME_PROP  = "username";
    public static final String PASSWORD_PROP  = "password";
    public static final String SCHEMA_PROP    = "schema";
    public static final String DRIVER_PROP    = "driver";

    /**
     * @parameter
     */
    private String contexts;
    
    /**
     * @parameter default-value="${project.basedir}/target/changelogs"
     */
    private File changeLogSavePath;

    /**
     * @parameter
     */
    private URL changeLogTagUrl;

    /**
     * @parameter default-value="${project.basedir}/src/main/changelogs"
     */
    private File changeLogPath;
    
    /**
     * @parameter
     */
    private Boolean dropFirst;
    
    /**
     *
     * @parameter
     */
    private String propertiesFile;

    /**
     *
     * @parameter
     */
    private Properties properties;
    
}
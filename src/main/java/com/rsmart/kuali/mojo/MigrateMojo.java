package com.rsmart.kuali.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.liquibase.maven.plugins.AbstractLiquibaseUpdateMojo;

import liquibase.exception.LiquibaseException;
import liquibase.Liquibase;

import java.io.File;
import java.net.URL;
import java.util.Properties;

/**
 * Migrate Liquibase changelogs
 *
 * @author Leo Przybylski
 * @goal migrate
 */
public class MigrateMojo extends AbstractLiquibaseUpdateMojo {

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
    private File updatePath;
    
    /**
     * Whether or not to perform a drop on the database before executing the change.
     * @parameter expression="${liquibase.dropFirst}" default-value="false"
     */
    protected boolean dropFirst;
    
    @Override
    protected void doUpdate(Liquibase liquibase) throws LiquibaseException {
        if (dropFirst) {
            liquibase.dropAll();
        }
        
        if (changesToApply > 0) {
            liquibase.update(changesToApply, contexts);
        } else {
            liquibase.update(contexts);
        }
    }
    
    @Override
    protected void printSettings(String indent) {
        super.printSettings(indent);
        getLog().info(indent + "drop first? " + dropFirst);

    }
}
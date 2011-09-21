// Copyright 2011 Leo Przybylski. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification, are
// permitted provided that the following conditions are met:
//
//    1. Redistributions of source code must retain the above copyright notice, this list of
//       conditions and the following disclaimer.
//
//    2. Redistributions in binary form must reproduce the above copyright notice, this list
//       of conditions and the following disclaimer in the documentation and/or other materials
//       provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY <COPYRIGHT HOLDER> ''AS IS'' AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
// FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
// ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// The views and conclusions contained in the software and documentation are those of the
// authors and should not be interpreted as representing official policies, either expressed
// or implied, of Leo Przybylski.
package com.rsmart.kuali.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.manager.WagonManager;

import org.liquibase.maven.plugins.AbstractLiquibaseChangeLogMojo;

import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;
import liquibase.serializer.ChangeLogSerializer;
import liquibase.parser.core.xml.LiquibaseEntityResolver;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

import liquibase.util.xml.DefaultXmlWriter;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

/**
 * Tests Liquibase changelogs against various databases
 *
 * @author Leo Przybylski
 * @goal test
 */
public class LiquibaseTestMojo extends AbstractLiquibaseChangeLogMojo {
    public static final String DEFAULT_CHANGELOG_PATH = "src/main/changelogs";
    public static final String TEST_ROLLBACK_TAG = "test";

    /**
     * The Maven project that plugin is running under.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Specifies the change log file to use for Liquibase. No longer needed with updatePath.
     * @parameter expression="${liquibase.changeLogFile}"
     * @deprecated
     */
    protected String changeLogFile;

    /**
     * @parameter default-value="${project.basedir}/target/changelogs"
     */
    protected File changeLogSavePath;

    /**
     * Location of an update.xml
     *
     * @parameter expression="${lb.updatePath}" default-value="${project.basedir}/src/main/changelogs"
     */
    protected File updatePath;
    
    /**
     * The tag to roll the database back to. 
     * @parameter expression="${liquibase.rollbackTag}"
     */
    protected String rollbackTag;
    

    protected File getBasedir() {
        return project.getBasedir();
    }

    @Override
    protected void performLiquibaseTask(Liquibase liquibase) throws LiquibaseException {
        super.performLiquibaseTask(liquibase);
        getLog().info("Doing it");
        /*
        rollbackTag = TEST_ROLLBACK_TAG;
        liquibase.tag(rollbackTag);
        liquibase.update(contexts);
        liquibase.rollback(rollbackTag, contexts);
        */
    }
}
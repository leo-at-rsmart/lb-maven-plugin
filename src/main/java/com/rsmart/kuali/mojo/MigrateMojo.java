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

import org.liquibase.maven.plugins.AbstractLiquibaseUpdateMojo;

import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;
import liquibase.serializer.ChangeLogSerializer;
import liquibase.parser.core.xml.LiquibaseEntityResolver;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;

import liquibase.util.xml.DefaultXmlWriter;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
 * Migrate Liquibase changelogs
 *
 * @author Leo Przybylski
 * @goal migrate
 */
public class MigrateMojo extends AbstractLiquibaseUpdateMojo {

    /**
     * Specifies the change log file to use for Liquibase. No longer needed with updatePath.
     * @parameter expression="${liquibase.changeLogFile}"
     * @deprecated
     */
    protected String changeLogFile;

    /**
     * @parameter default-value="${project.basedir}/target/changelogs"
     */
    private File changeLogSavePath;

    /**
     * @parameter
     */
    private URL changeLogTagUrl;

    /**
     * Location of an update.xml
     *
     * @parameter expression="${lb.updatePath}" default-value="${project.basedir}/src/main/changelogs"
     */
    private File updatePath;
    
    /**
     * Whether or not to perform a drop on the database before executing the change.
     * @parameter expression="${liquibase.dropFirst}" default-value="false"
     */
    protected boolean dropFirst;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        changeLogFile = new File(changeLogSavePath, "update.xml").getPath();
        
        final Collection<File> changelogs = scanForChangelogs(changeLogSavePath);
        
        try {
            generateUpdateLog(new File(changeLogFile), changelogs);
        }
        catch (Exception e) {
            throw new MojoExecutionException("Failed to generate changelog file " + changeLogFile, e);
        }
        
        super.execute();
    }

    protected Collection<File> scanForChangelogs(final File searchPath) {
        final Collection<File> retval = new ArrayList<File>();
        
        if (searchPath.getName().endsWith("update")) {
            return Arrays.asList(searchPath.listFiles());
        }
        
        if (searchPath.isDirectory()) {
            for (final File file : searchPath.listFiles()) {
                if (file.isDirectory()) {
                    retval.addAll(scanForChangelogs(file));
                }
            }
        }
        
        return retval;
    }

    protected void generateUpdateLog(final File changeLogFile, final Collection<File> changelogs) throws FileNotFoundException, IOException {
        changeLogFile.mkdirs();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder;
		try {
			documentBuilder = factory.newDocumentBuilder();
		}
		catch(ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		documentBuilder.setEntityResolver(new LiquibaseEntityResolver());

		Document doc = documentBuilder.newDocument();
		Element changeLogElement = doc.createElementNS(XMLChangeLogSAXParser.getDatabaseChangeLogNameSpace(), "databaseChangeLog");

		changeLogElement.setAttribute("xmlns", XMLChangeLogSAXParser.getDatabaseChangeLogNameSpace());
		changeLogElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		changeLogElement.setAttribute("xsi:schemaLocation", "http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-"+ XMLChangeLogSAXParser.getSchemaVersion()+ ".xsd");

		doc.appendChild(changeLogElement);

        for (final File changelog : changelogs) {
            doc.appendChild(includeNode(doc, changelog));
        }

        new DefaultXmlWriter().write(doc, new FileOutputStream(changeLogFile));
    }

    protected Element includeNode(final Document parentChangeLog, final File changelog) throws IOException {
        final Element retval = parentChangeLog.createElementNS(XMLChangeLogSAXParser.getDatabaseChangeLogNameSpace(), "include");
        retval.setAttribute("file", changelog.getCanonicalPath());
        return retval;
    }

    @Override
    protected void doUpdate(Liquibase liquibase) throws LiquibaseException {
        if (dropFirst) {
            dropAll(liquibase);
        }
        
        if (changesToApply > 0) {
            liquibase.update(changesToApply, contexts);
        } else {
            liquibase.update(contexts);
        }
    }

    /**
     * Drops the database. Makes sure it's done right the first time.
     *
     * @param liquibase
     * @throws LiquibaseException
     */
    protected void dropAll(final Liquibase liquibase) throws LiquibaseException {
        boolean retry = true;
        while (retry) {
            try {
                liquibase.dropAll();
                retry = false;
            }
            catch (LiquibaseException e2) {
                getLog().info(e2.getMessage());
                if (e2.getMessage().indexOf("ORA-02443") < 0 && e2.getCause() != null && retry) {
                    retry = (e2.getCause().getMessage().indexOf("ORA-02443") > -1);
                }
                
                if (!retry) {
                    throw e2;
                }
                else {
                    getLog().info("Got ORA-2443. Retrying...");
                }
            }
        }        
    }
    
    @Override
    protected void printSettings(String indent) {
        super.printSettings(indent);
        getLog().info(indent + "drop first? " + dropFirst);

    }
}
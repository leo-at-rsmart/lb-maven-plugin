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

import org.liquibase.maven.plugins.AbstractLiquibaseUpdateMojo;

import liquibase.Liquibase;
import liquibase.exception.LiquibaseException;
import liquibase.serializer.ChangeLogSerializer;
import liquibase.parser.core.xml.LiquibaseEntityResolver;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

import liquibase.util.xml.DefaultXmlWriter;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;

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

import static org.tmatesoft.svn.core.wc.SVNRevision.HEAD;
import static org.tmatesoft.svn.core.wc.SVNRevision.WORKING;

/**
 * Migrate Liquibase changelogs
 *
 * @author Leo Przybylski
 * @goal migrate
 */
public class MigrateMojo extends AbstractLiquibaseUpdateMojo {
    public static final String DEFAULT_CHANGELOG_PATH = "src/main/changelogs";

    protected String svnUsername;
    protected String svnPassword;

    /**
     * The server id in settings.xml to use when authenticating with.
     *
     * @parameter expression="${lb.svnServer}"
     */
    protected String svnServer;
    

    /**
     * The Maven Wagon manager to use when obtaining server authentication details.
     * @component role="org.apache.maven.artifact.manager.WagonManager"
     * @required
     * @readonly
     */
    protected WagonManager wagonManager;

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
     * @parameter expression="${lb.changeLogTagUrl}"
     */
    protected URL changeLogTagUrl;

    /**
     * Location of an update.xml
     *
     * @parameter expression="${lb.updatePath}" default-value="${project.basedir}/src/main/changelogs"
     */
    protected File updatePath;
    
    /**
     * Whether or not to perform a drop on the database before executing the change.
     * @parameter expression="${liquibase.dropFirst}" default-value="false"
     */
    protected boolean dropFirst;

    protected File getBasedir() {
        return project.getBasedir();
    }

    protected SVNURL getChangeLogTagUrl() throws SVNException {
        if (changeLogTagUrl == null) {
            return getProjectSvnUrlFrom(getBasedir()).appendPath("tags", true);
        }
        return SVNURL.parseURIEncoded(changeLogTagUrl.toString());
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (svnServer != null) {
            final AuthenticationInfo info = wagonManager.getAuthenticationInfo(svnServer);
            if (info != null) {
                svnUsername = info.getUserName();
                svnPassword = info.getPassword();
            }
        }
        DAVRepositoryFactory.setup();

        if (!isUpdateRequired()) {
            return;
        }


        try {
            for (final SVNURL tag : getTagUrls()) {
                final String tagBasePath = getLocalTagPath(tag);
                
                final File tagPath = new File(tagBasePath, "update");
                tagPath.mkdirs();
                
                final SVNURL changeLogUrl = tag.appendPath(DEFAULT_CHANGELOG_PATH + "/update", true);
                SVNClientManager.newInstance().getUpdateClient()
                    .doExport(changeLogUrl, tagPath, HEAD, HEAD, null, true, SVNDepth.INFINITY);
            }
        }
        catch (Exception e) {
            throw new MojoExecutionException("Exception when exporting changelogs from previous revisions", e);
        }

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

    protected String getLocalTagPath(final SVNURL tag) {
        final String tagPath = tag.getPath();
        return changeLogSavePath + File.separator + tagPath.substring(tagPath.lastIndexOf("/") + 1);
    }

    protected boolean isUpdateRequired() throws MojoExecutionException {
        try {
            getLog().debug("Comparing " + getCurrentRevision() + " to " + getLocalRevision());
            return getCurrentRevision() > getLocalRevision();
        }
        catch (Exception e) {
            throw new MojoExecutionException("Could not compare local and remote revisions ", e);
        }
    }

    protected SVNURL getProjectSvnUrlFrom(final File path) throws SVNException {
        SVNURL retval = getWCClient().doInfo(getBasedir(), HEAD).getURL();
        String removeToken = null;
        if (retval.getPath().indexOf("/branches") > -1) {
            removeToken = "/branches";
        }
        else if (retval.getPath().indexOf("/tags") > -1) {
            removeToken = "/tags";
        }
        else if (retval.getPath().indexOf("/trunk") > -1) {
            removeToken = "/trunk";
        }

        getLog().debug("Checking path " + retval.getPath() + " for token " + removeToken);
        while (retval.getPath().indexOf(removeToken) > -1) {
            retval = retval.removePathTail();
        }
        return retval;
    }

    protected Long getCurrentRevision() throws SVNException {
        return getWCClient().doInfo(getBasedir(), HEAD).getCommittedRevision().getNumber();
    }

    protected Long getLocalRevision() throws SVNException {
        return getWCClient().doInfo(getBasedir(), WORKING).getRevision().getNumber();
    }

    protected Long getTagRevision(final String tag) throws SVNException {
        return getWCClient().doInfo(getChangeLogTagUrl(), WORKING, WORKING).getRevision().getNumber();
    }

    protected Collection<SVNURL> getTagUrls() throws SVNException {
        final Collection<SVNURL> retval = new ArrayList<SVNURL>();
        getLog().debug("Looking up tags in " + getChangeLogTagUrl().toString());
        clientManager().getLogClient()
            .doList(getChangeLogTagUrl(), HEAD, HEAD, false, false, 
                    new ISVNDirEntryHandler() {
                        public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
                            if (dirEntry.getRevision() >= getLocalRevision()
                                && dirEntry.getPath().trim().length() > 0) {
                                getLog().debug("Adding tag '" + dirEntry.getPath() + "'");
                                retval.add(dirEntry.getURL());
                            }
                        }
                    });
        return retval;
    }

    protected SVNWCClient getWCClient() {
        return clientManager().getWCClient();
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

    protected SVNClientManager clientManager() {
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager("lprzybylski", "entr0py0");
        ISVNOptions options = SVNWCUtil.createDefaultOptions(true);       
        SVNClientManager clientManager = SVNClientManager.newInstance(options, authManager);
        
        return clientManager;
    }

    protected void generateUpdateLog(final File changeLogFile, final Collection<File> changelogs) throws FileNotFoundException, IOException {
        changeLogFile.getParentFile().mkdirs();

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
            doc.getDocumentElement().appendChild(includeNode(doc, changelog));
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
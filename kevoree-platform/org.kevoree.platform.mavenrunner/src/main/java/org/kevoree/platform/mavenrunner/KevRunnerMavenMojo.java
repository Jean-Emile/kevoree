/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kevoree.platform.mavenrunner;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kevoree.ContainerRoot;
import org.kevoree.platform.standalone.App;
import org.kevoree.platform.standalone.KevoreeBootStrap;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;

import java.io.File;

/**
 * User: Francois Fouquet - fouquet.f@gmail.com
 * Date: 15/03/12
 * Time: 12:58
 *
 * @author Francois Fouquet
 * @version 1.0
 * @goal run
 * @phase install
 * @requiresDependencyResolution compile+runtime
 */
public class KevRunnerMavenMojo extends AbstractMojo {

    /**
     * @parameter default-value="${project.basedir}/src/main/kevs/main.kevs"
     */
    private File kevsFile;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;


    public void execute() throws MojoExecutionException {


        try {
            KevoreeBootStrap.byPassAetherBootstrap =true;
            org.kevoree.tools.aether.framework.AetherUtil.setRepositorySystemSession(repoSession);
            org.kevoree.tools.aether.framework.AetherUtil.setRepositorySystem(repoSystem);
            ContainerRoot model = KevScriptHelper.generate(kevsFile,project);


            File tFile = new File(project.getBuild().getOutputDirectory(), "runner.kev");
            org.kevoree.framework.KevoreeXmiHelper.save(tFile.getAbsolutePath(), model);

            System.setProperty("node.bootstrap", tFile.getAbsolutePath());
            System.setProperty("node.name", "node0");

            App.main(new String[0]);


            Thread.currentThread().join();


        } catch (Exception e) {
            getLog().error(e);
        }


    }


}

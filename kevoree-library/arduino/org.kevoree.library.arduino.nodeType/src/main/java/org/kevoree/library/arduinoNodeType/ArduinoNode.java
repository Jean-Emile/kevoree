package org.kevoree.library.arduinoNodeType;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kevoree.ContainerRoot;
import org.kevoree.KevoreeFactory;
import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.Library;
import org.kevoree.annotation.NodeType;
import org.kevoree.framework.AbstractNodeType;
import org.kevoree.kompare.KevoreeKompareBean;
import org.kevoreeAdaptation.AdaptationModel;
import org.osgi.framework.BundleContext;
import org.wayoda.ang.libraries.CodeManager;
import org.wayoda.ang.libraries.Core;
import org.wayoda.ang.project.ArduinoBuildEnvironment;
import org.wayoda.ang.project.Sketch;
import org.wayoda.ang.project.Target;
import org.wayoda.ang.project.TargetDirectoryService;

@NodeType
@Library(name = "KevoreeNodeType")
@DictionaryType({
        @DictionaryAttribute(name = "boardTypeName", defaultValue = "uno", optional = true),
        @DictionaryAttribute(name = "boardPortName")
})
public class ArduinoNode extends AbstractNodeType {

    @Override
    public void push(String targetNodeName, ContainerRoot root, BundleContext bundle) {
        System.out.println("I'm the arduino deployer");



        bcontext = bundle;

        //STEP 0 : FOUND ARDUINO COMMUNICATION CHANNEL

        //  ContainerRoot newModel = KevoreeXmiHelper.load("/Users/ffouquet/Documents/DEV/dukeboard_github/kevoree/kevoree-library/org.kevoree.library.arduino.nodeType/src/test/resources/models/TempSensorAlone.kev");

        // Generator.generate(containerRoot);
        KevoreeKompareBean kompare = new KevoreeKompareBean();
        AdaptationModel kompareModel = kompare.kompare(KevoreeFactory.eINSTANCE.createContainerRoot(), root, targetNodeName);

        //STEP 1 : GENERATE FLAT CODE - MODEL SPECIFIQUE
        //STEP 2 : COMPILE to PDE Target
        File newdir = new File("arduinoGenerated" + targetNodeName);

        File newdirTarget = new File("arduinoGenerated" + targetNodeName + "/target");
        newdirTarget.mkdir();
        TargetDirectoryService.rootPath = newdirTarget.getAbsolutePath();

        newdir.mkdir();

        outputPath = newdir.getAbsolutePath();
        System.out.println("outDir=" + outputPath);
        deploy(kompareModel, targetNodeName);


        //STEP 3 : Deploy by commnication channel
        ArduinoCompilation arduinoCompilation = new ArduinoCompilation();
        ArduinoLink arduinoLink = new ArduinoLink();
        ArduinoArchive arduinoArchive = new ArduinoArchive();
        ArduinoPostCompilation arduinoPostCompilation = new ArduinoPostCompilation();
        ArduinoDeploy arduinoDeploy = new ArduinoDeploy();
        arduinoCompilation.prepareCommands();
        arduinoLink.prepareCommands();
        arduinoArchive.prepareCommands();
        arduinoPostCompilation.prepareCommands();
        arduinoDeploy.prepareCommands();


        ArduinoBuildEnvironment arduinoBuildEnvironment = ArduinoBuildEnvironment.getInstance();
        Target target = arduinoBuildEnvironment.getDefaultTargetList().getTarget(this.getDictionary().get("boardTypeName").toString());
        try {
            Sketch sketch = new Sketch(newdir);
            sketch.preprocess(target);

            Core core = CodeManager.getInstance().getCore(target);
            arduinoCompilation.compileCore(sketch, target, core);

            for (org.wayoda.ang.libraries.Library library : sketch.getLibraries()) {

                System.out.println("----Lib "+library.getName());

                arduinoCompilation.compileLibrary(sketch, target, library, core);
            }

            arduinoCompilation.compileSketch(sketch, target, core);

            arduinoArchive.archiveSketch(sketch, target);

            arduinoLink.linkSketch(sketch, target);

            arduinoPostCompilation.postCompileSketch(sketch, target);

            System.out.println("boardPortName="+this.getDictionary().get("boardPortName"));

            arduinoDeploy.uploadSketch(sketch, target,this.getDictionary().get("boardPortName").toString());


        } catch (FileNotFoundException ex) {
            Logger.getLogger(ArduinoNode.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    private String outputPath = "";

    private BundleContext bcontext = null;

    @Override
    public boolean deploy(AdaptationModel model, String nodeName) {

        //Step : Type Bundle preparation step
        if (bcontext != null) {

            System.out.println("Install Type definition");

            TypeBundleBootstrap.bootstrapTypeBundle(model, bcontext);
        } else {
            System.out.println("Warning no OSGi runtime available");
        }

        //Step : Generate firmware code to output path
        Generator.generate(model, nodeName, outputPath,bcontext);

        return true;
    }
}

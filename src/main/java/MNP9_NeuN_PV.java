/*
 * Colocalization NeuN/PV: Neun/PV+ versus NeuN/PV
 * Cells volume intensity in MNP9 channel
 */

import MNP9_NeuN_PV_Tools.Tools;
import ij.*;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.io.FilenameUtils;



public class MNP9_NeuN_PV implements PlugIn {
    
    Tools tools = new Tools();
    
    private String imageDir = "";
    public String outDirResults = "";
    private boolean canceled = false;
    private String file_ext = "czi";
    public BufferedWriter results_analyze;
   
    
    
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage(" Pluging canceled");
                return;
            }   
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }   
            // Find images with extension
            file_ext = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, file_ext);
            if (imageFiles == null) {
                IJ.showMessage("Error", "No images found with "+file_ext+" extension");
                return;
            }
            // create output folder
            outDirResults = imageDir + File.separator+ "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            // Find channel names , calibration
            reader.setId(imageFiles.get(0));
            tools.cal = tools.findImageCalib(meta);
            String[] chsName = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Write header
            String header= "Image Name\t#NeuN cell\tNeuN volume (µm3)\tPV positive\tTotal cell intensity\tMean cell intensity\tMNP9 bg mean intensity\n";
            FileWriter fwNucleusGlobal = new FileWriter(outDirResults + "results.xls", false);
            results_analyze = new BufferedWriter(fwNucleusGlobal);
            results_analyze.write(header);
            results_analyze.flush();
            
            // Channels dialog
            boolean pv = (reader.getSizeC() == 3) ? true : false;
            int[] channelsIndex = tools.dialog(chsName);
            if (channelsIndex == null) {
                IJ.showStatus("Plugin cancelled");
                return;
            }

            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                reader.setId(f);
                ImporterOptions options = new ImporterOptions();
                
                /**
                * read channels
                */
                
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                
                // open NeuN Channel
                System.out.println("--- Opening NeuN channel  ...");
                ImagePlus imgNeuN = BF.openImagePlus(options)[channelsIndex[0]];
                Objects3DIntPopulation neunPop = tools.cellPoseCellsPop(imgNeuN);
                int neunCells = neunPop.getNbObjects();
                System.out.println(neunCells+" NeuN cells found");
                
                // Find PV cells
                ImagePlus imgPV = null;
                Objects3DIntPopulation pvPop = new Objects3DIntPopulation();
                if (pv) {
                    System.out.println("--- Opening PV channel ...");
                    imgPV = BF.openImagePlus(options)[channelsIndex[1]];
                    // Find PV cells
                    pvPop = tools.cellPoseCellsPop(imgPV);
                    int pvCells = pvPop.getNbObjects();
                    System.out.println(pvCells+" PV cells found");
                    // Find Neun/PV+ cells
                    int neunPvCells = tools.findNumberColocPop(neunPop, pvPop, 0.5);
                    System.out.println(neunPvCells + " NeuN cells PV+ found");
                    tools.flush_close(imgPV);
                }
                
                // Save image objects
                tools.saveImgObjects(neunPop, pvPop, rootName+"_Objects.tif", imgNeuN, outDirResults);
                tools.flush_close(imgNeuN);
                
                //open MNP9 Channel
                ImagePlus imgMNP9 = (pv) ? BF.openImagePlus(options)[channelsIndex[2]] : BF.openImagePlus(options)[channelsIndex[1]];
                
                // measure NeuN cells volume and intensity in channel MNP9
                tools.writeCellsParameters(neunPop, imgMNP9, rootName, results_analyze);
            }
            results_analyze.close();
        } 
        catch (DependencyException | ServiceException | FormatException | IOException  ex) {
            Logger.getLogger(MNP9_NeuN_PV.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    

/*
 * Colocalization NeuN/ soma microgly (IB4)
 * Cells volume intensity in protein channel
 */

import NeuN_IBA1_Astro_Prots_Tools.Tools;
import ij.*;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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



public class NeuN_IBA1_Astro_Prots implements PlugIn {
    
    NeuN_IBA1_Astro_Prots_Tools.Tools tools = new Tools();
    
    private String imageDir = "";
    public String outDirResults = "";
    private boolean canceled = false;
    
   
    
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
            String file_ext = tools.findImageType(new File(imageDir));
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
            
            // Channels dialog
            int[] channelsIndex = tools.dialog(chsName);
            if (channelsIndex == null) {
                IJ.showStatus("Plugin cancelled");
                return;
            }
            
            // headers for results
            // NeuN
            FileWriter fwNeunCells = (chsName[channelsIndex[0]].equals("None")) ? null : new FileWriter(outDirResults + "NeunCells_results.xls", false);
            BufferedWriter neuNResults = (fwNeunCells == null) ? null : new BufferedWriter(fwNeunCells);
            String neuNHeader = (neuNResults == null) ? "" : "\t#NeuN cell\tNeuN volume (µm3)";
            String neuNIntProtAHeader = (chsName[channelsIndex[0]].equals("None")) & (chsName[channelsIndex[3]].equals("None")) ? "" : 
                    "\tNeuN cell intensity in ProtA";
            String neuNIntProtBHeader = (chsName[channelsIndex[0]].equals("None")) & (chsName[channelsIndex[4]].equals("None")) ? "" : 
                    "\tNeuN cell intensity in ProtB";
            String neuNIntProtCHeader = (chsName[channelsIndex[0]].equals("None")) & (chsName[channelsIndex[5]].equals("None")) ? "" : 
                    "\tNeuN cell intensity in ProtC";
            // Write header
            String header= "Image Name"+neuNHeader+neuNIntProtAHeader+neuNIntProtBHeader+neuNIntProtCHeader+"\n";
            if (neuNResults != null) {
                neuNResults.write(header);
                neuNResults.flush();
            }
            

            // IBA1
            FileWriter fwIba1Cells = (chsName[channelsIndex[1]].equals("None")) ? null : new FileWriter(outDirResults + "Iba1Cells_results.xls", false);
            BufferedWriter iba1Results = (fwIba1Cells == null) ? null : new BufferedWriter(fwIba1Cells);
            String iba1Header = (chsName[channelsIndex[1]].equals("None")) ? "" : "\t#IBA1 cell\tIBA1 volume (µm3)";
            String iba1IntProtAHeader = (chsName[channelsIndex[1]].equals("None")) & (chsName[channelsIndex[3]].equals("None")) ? "" : 
                    "\tIBA1 cell intensity in ProtA";
            String iba1IntProtBHeader = (chsName[channelsIndex[1]].equals("None")) & (chsName[channelsIndex[4]].equals("None")) ? "" : 
                    "\tIBA1 cell intensity in ProtB";
            String iba1IntProtCHeader = (chsName[channelsIndex[1]].equals("None")) & (chsName[channelsIndex[5]].equals("None")) ? "" : 
                    "\tIBA1 cell intensity in ProtC";// Write header
            header= "Image Name"+iba1Header+iba1IntProtAHeader+iba1IntProtBHeader+iba1IntProtCHeader+"\n";
            if (iba1Results != null) {
                iba1Results.write(header);
                iba1Results.flush();
            }
            
            
            // Astro
            FileWriter fwAstroCells = (chsName[channelsIndex[2]].equals("None")) ? null : new FileWriter(outDirResults + "AstroCells_results.xls", false);
            BufferedWriter astroResults = (fwAstroCells == null) ? null : new BufferedWriter(fwAstroCells);
            String astroHeader = (chsName[channelsIndex[2]].equals("None")) ? "" : "\t#Astro cell\tAstro volume (µm3)";
            String astroIntProtAHeader = (chsName[channelsIndex[2]].equals("None")) & (chsName[channelsIndex[3]].equals("None")) ? "" : 
                    "\tAstro cell intensity in ProtA";
            String astroIntProtBHeader = (chsName[channelsIndex[2]].equals("None")) & (chsName[channelsIndex[4]].equals("None")) ? "" : 
                    "\tAstro cell intensity in ProtB";
            String astroIntProtCHeader = (chsName[channelsIndex[2]].equals("None")) & (chsName[channelsIndex[5]].equals("None")) ? "" : 
                    "\tAstro cell intensity in ProtC";// Write header
            header= "Image Name"+astroHeader+astroIntProtAHeader+astroIntProtBHeader+astroIntProtCHeader+"\n";
            if (astroResults != null) {
                astroResults.write(header);
                astroResults.flush();
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
                
                System.out.println("--- Opening ProtA channel  ...");
                ImagePlus imgProtA = BF.openImagePlus(options)[channelsIndex[3]];
                ImagePlus imgProtB = (chsName[channelsIndex[4]].equals("None")) ? null : BF.openImagePlus(options)[channelsIndex[4]];
                ImagePlus imgProtC = (chsName[channelsIndex[5]].equals("None")) ? null : BF.openImagePlus(options)[channelsIndex[5]];
                
                // NeuN
                if (!neuNHeader.equals("")) {
                    System.out.println("--- Opening NeuN channel  ...");
                    ImagePlus imgNeuN = BF.openImagePlus(options)[channelsIndex[0]];
                    Objects3DIntPopulation neunPop = tools.cellPoseCellsPop(imgNeuN, tools.cellPoseModel_NeuN, tools.cellPoseNeuNDiameter);
                    int neunCells = neunPop.getNbObjects();
                    System.out.println(neunCells+" NeuN cells found");
                    // save images objects
                    tools.saveImgObjects(neunPop, rootName+"_NeuN_Objects.tif", imgNeuN, outDirResults);
                    // write cells volume and intensity in prot channel
                    tools.writeCellsParameters(neunPop, imgProtA, imgProtB, imgProtC, rootName, neuNResults);
                    tools.flush_close(imgNeuN);
                }
                
                // Iba1
                if (!iba1Header.equals("")) {
                    System.out.println("--- Opening Iba1 channel  ...");
                    ImagePlus imgIba1 = BF.openImagePlus(options)[channelsIndex[1]];
                    Objects3DIntPopulation iba1Pop = tools.cellPoseCellsPop(imgIba1, tools.cellPoseModel_Iba1, tools.cellPoseIba1Diameter);
                    int iba1Cells = iba1Pop.getNbObjects();
                    System.out.println(iba1Cells+" Iba1 cells found");
                    // save images objects
                    tools.saveImgObjects(iba1Pop, rootName+"_Iba1_Objects.tif", imgIba1, outDirResults);
                    // write cells volume and intensity in prot channel
                    tools.writeCellsParameters(iba1Pop, imgProtA, imgProtB, imgProtC, rootName, iba1Results);
                    tools.flush_close(imgIba1);
                }
                
                // Astrocyte
                if (!astroHeader.equals("")) {
                    System.out.println("--- Opening Astrocyte channel  ...");
                    ImagePlus imgAstro = BF.openImagePlus(options)[channelsIndex[2]];
                    Objects3DIntPopulation astroPop = tools.stardistObjectsPop(imgAstro);
                    int astroCells = astroPop.getNbObjects();
                    System.out.println(astroCells+" Astrocyte cells found");
                    // save images objects
                    tools.saveImgObjects(astroPop, rootName+"_Astro_Objects.tif", imgAstro, outDirResults);
                    // write cells volume and intensity in prot channel
                    tools.writeCellsParameters(astroPop, imgProtA, imgProtB, imgProtC, rootName, astroResults);
                    tools.flush_close(imgAstro);
                }
                tools.flush_close(imgProtA);
                if (imgProtB != null)
                    tools.flush_close(imgProtB);
                if (imgProtC != null)
                    tools.flush_close(imgProtC);
            }
            if (!neuNHeader.equals(""))
                neuNResults.close();
            if (!iba1Header.equals(""))
                iba1Results.close();
            if (!astroHeader.equals(""))
                astroResults.close();
        } 
        catch (DependencyException | ServiceException | FormatException | IOException  ex) {
            Logger.getLogger(NeuN_IBA1_Astro_Prots.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }
}    

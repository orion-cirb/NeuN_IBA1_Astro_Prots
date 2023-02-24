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


/**
 * Detect NeuN cells/IBA1 microglia/Astrocytes and read their intensity in various protein channels
 * @author ORION-CIRB
 */
public class NeuN_IBA1_Astro_Prots implements PlugIn {
    
    NeuN_IBA1_Astro_Prots_Tools.Tools tools = new Tools();
    private String imageDir = "";
    public String outDirResults = "";
    
   
    public void run(String arg) {
        try {
            if (!tools.checkInstalledModules()) {
                IJ.showMessage(" Plugin canceled");
                return;
            }
            
            // Get input folder
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
            
            // Create output folder
            outDirResults = imageDir + File.separator+ "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));

            // Find image calibration
            tools.findImageCalib(meta);
            
            // Find channels name
            String[] chsName = tools.findChannels(imageFiles.get(0), meta, reader);
            
            // Generate dialog box
            int[] channelsIndex = tools.dialog(chsName);
            if (channelsIndex == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }
            
            // Write headers in results files
            String protAHeader = "\tProteinA bg\tCells corr ProteinA mean int";
            String protBHeader = (chsName[channelsIndex[4]].equals("None")) ? "" : "\tProteinB bg\tCells corr ProteinB mean int";
            String protCHeader = (chsName[channelsIndex[5]].equals("None")) ? "" : "\tProteinC bg\tCells corr ProteinA mean int";
            String header= "Image name\tCells nb\tCells total vol (µm3)"+protAHeader+protBHeader+protCHeader+"\n";
            // NeuN cells
            FileWriter fwNeunCells = (chsName[channelsIndex[0]].equals("None")) ? null : new FileWriter(outDirResults + "results_neun.xls", false);
            BufferedWriter neunResults = (fwNeunCells == null) ? null : new BufferedWriter(fwNeunCells);
            if (neunResults != null) {
                neunResults.write(header);
                neunResults.flush();
            }
            // IBA1 microglia
            FileWriter fwIba1Cells = (chsName[channelsIndex[1]].equals("None")) ? null : new FileWriter(outDirResults + "results_iba1.xls", false);
            BufferedWriter iba1Results = (fwIba1Cells == null) ? null : new BufferedWriter(fwIba1Cells);
            if (iba1Results != null) {
                iba1Results.write(header);
                iba1Results.flush();
            }
            // Astrocytes
            FileWriter fwAstroCells = (chsName[channelsIndex[2]].equals("None")) ? null : new FileWriter(outDirResults + "results_astro.xls", false);
            BufferedWriter astroResults = (fwAstroCells == null) ? null : new BufferedWriter(fwAstroCells);
            if (astroResults != null) {
                astroResults.write(header);
                astroResults.flush();
            }
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                System.out.println("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                System.out.println("- Opening proteins channels -");
                ImagePlus imgProtA = BF.openImagePlus(options)[channelsIndex[3]];
                ImagePlus imgProtB = (chsName[channelsIndex[4]].equals("None")) ? null : BF.openImagePlus(options)[channelsIndex[4]];
                ImagePlus imgProtC = (chsName[channelsIndex[5]].equals("None")) ? null : BF.openImagePlus(options)[channelsIndex[5]];
                
                // NeuN cells
                if (neunResults != null) {
                    System.out.println("- Analyzing NeuN cells channel -");
                    ImagePlus imgNeuN = BF.openImagePlus(options)[channelsIndex[0]];
                    System.out.println("Finding NeuN cells...");
                    Objects3DIntPopulation neunPop = tools.cellposeDetection(imgNeuN, tools.cellPoseNeuNModel, tools.cellPoseNeuNDiameter);
                    System.out.println(neunPop.getNbObjects()+" NeuN cells found");
                    
                    tools.writeCellsParameters(neunPop, null, imgProtA, imgProtB, imgProtC, rootName, neunResults);
                    tools.drawResults(neunPop, null, rootName+"_neun", imgNeuN, outDirResults);
                    tools.flush_close(imgNeuN);
                }
                
                // IBA1 microglia
                if (iba1Results != null) {
                    System.out.println("- Analyzing IBA1 microglia channel -");
                    ImagePlus imgIba1 = BF.openImagePlus(options)[channelsIndex[1]];
                    System.out.println("Finding IBA1 microglia...");
                    Objects3DIntPopulation iba1Pop = tools.cellposeDetection(imgIba1, tools.cellPoseIba1Model, tools.cellPoseIba1Diameter);
                    System.out.println(iba1Pop.getNbObjects()+" Iba1 cells found");
                    // Find IBA1 mask of soma and processes
                    ImagePlus iba1Cellsmask = tools.detectIba1Cells(imgIba1); 
                    
                    tools.writeCellsParameters(iba1Pop, iba1Cellsmask, imgProtA, imgProtB, imgProtC, rootName, iba1Results);
                    tools.drawResults(iba1Pop, iba1Cellsmask, rootName+"_iba1", imgIba1, outDirResults);
                    tools.flush_close(imgIba1);
                }
                
                // Astrocytes
                if (astroResults != null) {
                    System.out.println("- Analyzing astrocytes channel -");
                    ImagePlus imgAstro = BF.openImagePlus(options)[channelsIndex[2]];
                    System.out.println("Finding astrocytes...");
                    Objects3DIntPopulation astroPop = tools.cellposeDetection(imgAstro, tools.cellPoseAstroModel, tools.cellPoseAstroDiameter);
                    System.out.println(astroPop.getNbObjects()+" astrocytes found");
                    
                    tools.writeCellsParameters(astroPop, null, imgProtA, imgProtB, imgProtC, rootName, astroResults);
                    tools.drawResults(astroPop, null, rootName+"_astro", imgAstro, outDirResults);
                    tools.flush_close(imgAstro);
                }
                
                tools.flush_close(imgProtA);
                if (imgProtB != null) tools.flush_close(imgProtB);
                if (imgProtC != null) tools.flush_close(imgProtC);
            }
            if (neunResults != null) neunResults.close();
            if (iba1Results != null) iba1Results.close();
            if (astroResults != null) astroResults.close();
        } 
        catch (DependencyException | ServiceException | FormatException | IOException  ex) {
            Logger.getLogger(NeuN_IBA1_Astro_Prots.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        System.out.println("--- All done! ---");
    }
}    

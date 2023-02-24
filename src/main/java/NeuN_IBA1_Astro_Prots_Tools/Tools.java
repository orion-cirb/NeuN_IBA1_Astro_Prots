package NeuN_IBA1_Astro_Prots_Tools;

import NeuN_IBA1_Astro_Prots_Tools.Cellpose.CellposeSegmentImgPlusAdvanced;
import NeuN_IBA1_Astro_Prots_Tools.Cellpose.CellposeTaskSettings;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;


/** 
 * @author ORION-CIRB
 */
public class Tools {

    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    String[] channelNames = {"NeuN cells", "IBA1 microglia", "Astrocytes", "Protein A (mandatory)", "Protein B", "Protein C"};
    public Calibration cal;
    public double pixVol = 0;

     // Cellpose
    private String cellPoseEnvDirPath = (IJ.isWindows()) ? System.getProperty("user.home")+"\\miniconda3\\envs\\CellPose\\" : "/opt/miniconda3/envs/cellpose/";
    private String cellposeModelPath = IJ.isWindows()? System.getProperty("user.home")+"\\.cellpose\\models\\" : "";
    public final String cellPoseNeuNModel = "cyto2";
    public final String cellPoseIba1Model = cellposeModelPath+"cyto2_Iba1_microglia";
    public final String cellPoseAstroModel = "cyto2";
    public int cellPoseNeuNDiameter = 80;
    public int cellPoseIba1Diameter = 40;
    public int cellPoseAstroDiameter = 50;
    
    public double minCellVol= 40;
    public double maxCellVol = 4000;
    
    public CLIJ2 clij2 = CLIJ2.getInstance();
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find extension of images in folder
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
                case "nd" :
                   ext = fileExt;
                   break;
                case "nd2" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "lsm" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;
                case "tiff" :
                    ext = fileExt;
                    break;
            }
        }
        return(ext);
    }
    
    
    /**
     * Find images in folder
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExtension) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in " + imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExtension) && !f.startsWith("."))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public void findImageCalib(IMetadata meta) {
        cal = new Calibration();  
        // Read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
    }
    
    
     /**
     * Find channels name
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs+1];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "nd2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelName(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelName(0, n).toString();
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n).toString();
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelFluor(0, n).toString().equals("")) ? Integer.toString(n) : meta.getChannelFluor(0, n).toString();
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelID(0, n) == null) ? channels[n] = Integer.toString(n) : meta.getChannelExcitationWavelength(0, n).value().toString();
                break;    
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    channels[n] = (meta.getChannelID(0, n) == null) ? channels[n] = Integer.toString(n) : meta.getChannelExcitationWavelength(0, n).value().toString();
                break; 
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        channels[chs] = "None";
        return(channels);     
    }
    
    
    /**
     * Generate dialog box
     */
    public int[] dialog(String[] chs) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 80, 0);
        gd.addImage(icon);
        
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            String ch = (index < chs.length) ? chs[index] : chs[chs.length-1];
            gd.addChoice(chNames+" : ", chs, ch);
            index++;
        }
        
        gd.addMessage("Cells detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min cell volume (µm3): ", minCellVol);
        gd.addNumericField("Max cell volume (µm3): ", maxCellVol);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Pixel size XY (µm): ", cal.pixelWidth);
        gd.addNumericField("Pixel size Z (µm): ", cal.pixelDepth);
        gd.showDialog();

        int[] chChoices = new int[channelNames.length];
        for (int n = 0; n < chChoices.length; n++)
            chChoices[n] = ArrayUtils.indexOf(chs, gd.getNextChoice());

        minCellVol = gd.getNextNumber();
        maxCellVol = gd.getNextNumber();
        
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        
        if (gd.wasCanceled())
            return(null);
        return(chChoices);
    }
    
    
    // Flush and close images
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }

    
    /**
     * Look for all 3D cells in a Z-stack: 
     * - apply CellPose in 2D slice by slice 
     * - let CellPose reconstruct cells in 3D using the stitch threshold parameters
     */
    public Objects3DIntPopulation cellposeDetection(ImagePlus img, String model, int diameter){
        float resizeFactor = 0.5f;
        ImagePlus imgResize = img.resize((int)(img.getWidth()*resizeFactor), (int)(img.getHeight()*resizeFactor), 1, "none");

        CellposeTaskSettings settings = new CellposeTaskSettings(model, 1, diameter, cellPoseEnvDirPath);
        settings.setStitchThreshold(0.5); 
        settings.useGpu(true);
        
        CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgResize);
        ImagePlus imgOut = cellpose.run();
        imgOut = imgOut.resize(img.getWidth(), img.getHeight(), 1, "none");
        imgOut.setCalibration(cal);
        
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgOut));
        int nbDetections = pop.getNbObjects();
        System.out.println(nbDetections + " detections");
        zFilterPop(pop);
        sizeFilterPop(pop, minCellVol, maxCellVol);
        System.out.println((nbDetections-pop.getNbObjects()) + " detections filtered out by size");
        pop.resetLabels();
        
        flush_close(imgResize);
        flush_close(imgOut);
        return(pop);
    }
     
    
    /**
     * Remove objects that appear in only one z-slice
     */
    public void zFilterPop(Objects3DIntPopulation pop) {
        pop.getObjects3DInt().removeIf(p -> (p.getObject3DPlanes().size() == 1));
    }
    
    
    /**
     * Remove objects with size outside of given range
     */
    public void sizeFilterPop(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
    }
       

    /**
     * Detect IBA1 microglia soma and processes
     */
    public ImagePlus detectIba1Cells(ImagePlus imgIba1) {
        ImagePlus imgMed = median_filter(imgIba1, 2, 2);
        ImagePlus imgBin = threshold(imgMed, "Li");
        ImagePlus imgOpen = open(imgBin, 1);
        
        flush_close(imgMed);
        flush_close(imgBin);
        return(imgOpen);
    }
    
    
    /**
     * Median filtering using CLIJ2
     */ 
    public ImagePlus median_filter(ImagePlus img, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.mean3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        ImagePlus imgMed = clij2.pull(imgCLMed);
        clij2.release(imgCLMed);
        return(imgMed);
    }
    
    
    /**
     * Thresholding using CLIJ2
     */
    private ImagePlus threshold(ImagePlus img, String thMed) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        clij2.release(imgCL);
        ImagePlus imgBin = clij2.pull(imgCLBin);
        clij2.release(imgCLBin);
        return(imgBin);
    }
    
    
    /**
     * Opening using CLIJ2
     */
    private ImagePlus open(ImagePlus img, int iter) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLOpen = clij2.create(imgCL);
        clij2.openingBox(imgCL, imgCLOpen, iter);
        clij2.release(imgCL);
        ImagePlus imgOpen = clij2.pull(imgCLOpen);
        clij2.release(imgCLOpen);
        return(imgOpen);
    }
    

    /**
     * Compute and save cells parameters
     */
    public void writeCellsParameters(Objects3DIntPopulation pop, ImagePlus imgMask, ImagePlus imgProtA, ImagePlus imgProtB, ImagePlus imgProtC, 
            String imgName, BufferedWriter results) throws IOException {
        if (pop.getNbObjects() != 0) {
            // Measure background
            double bgProtA = findBackground(imgProtA, "ProteinA");
            double bgProtB = (imgProtB == null) ? 0 : findBackground(imgProtB, "ProteinB");
            double bgProtC = (imgProtC == null) ? 0 : findBackground(imgProtC, "ProteinC");
            
            // Measure cells intensities
            double volProt = 0;
            double intProtA = 0;
            double intProtB = 0;
            double intProtC = 0;
            if (imgMask == null) {
                for (Object3DInt obj: pop.getObjects3DInt()) {
                    volProt += new MeasureVolume(obj).getVolumePix();
                    intProtA += new MeasureIntensity(obj, ImageHandler.wrap(imgProtA)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                    intProtB += (imgProtB == null) ? 0 : new MeasureIntensity(obj, ImageHandler.wrap(imgProtB)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                    intProtC += (imgProtC == null) ? 0 : new MeasureIntensity(obj, ImageHandler.wrap(imgProtC)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                }
            } else {
                double[] maskParams = computeMaskCellsParameters(imgMask, imgProtA);
                volProt = maskParams[0];
                intProtA = maskParams[1];
                intProtB = (imgProtB == null) ? 0 : computeMaskCellsParameters(imgMask, imgProtB)[1];
                intProtC = (imgProtC == null) ? 0 : computeMaskCellsParameters(imgMask, imgProtC)[1];
            }
            intProtA = intProtA/volProt - bgProtA;
            intProtB = intProtB/volProt - bgProtB;
            intProtC = intProtC/volProt - bgProtC;
            volProt = volProt*pixVol;
                
            String protA = "\t"+bgProtA+"\t"+intProtA;
            String protB = (imgProtB == null) ? "" : "\t"+bgProtB+"\t"+intProtB;
            String protC = (imgProtC == null) ? "" : "\t"+bgProtC+"\t"+intProtC;
            results.write(imgName+"\t"+pop.getNbObjects()+"\t"+volProt+protA+protB+protC+"\n");
            results.flush();
        }
    }
    
    
    /**
     * Find background image intensity:
     * Z projection over min intensity + read median intensity
     */
    public double findBackground(ImagePlus img, String chName) {
      ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
      ImageProcessor imp = imgProj.getProcessor();
      double bg = imp.getStatistics().median;
      System.out.println(chName+" background (median of the min projection) = " + bg);
      flush_close(imgProj);
      return(bg);
    }
    
    
    /**
     * Perform z-projection of a stack
     */
    public ImagePlus doZProjection(ImagePlus img, int param) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(param);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
    
   
    public double[] computeMaskCellsParameters(ImagePlus mask, ImagePlus img) {
        ResultsTable rt = new ResultsTable();
        Analyzer analyzer = new Analyzer(img, Analyzer.AREA+Analyzer.INTEGRATED_DENSITY, rt);
        double area = 0;
        double intSum = 0;
        for (int n = 1; n <= mask.getNSlices(); n++) {
            mask.setSlice(n);
            IJ.setRawThreshold(mask, 1, 65535);
            IJ.run(mask, "Create Selection", "");
            Roi roi = mask.getRoi();
            
            img.setSlice(n);
            img.setRoi(roi);
            rt.reset();
            analyzer.measure();
            area += rt.getValue("Area", 0);
            intSum += rt.getValue("RawIntDen",0);
        }
        double[] volInt = {area*cal.pixelDepth/pixVol, intSum};
        return(volInt);
    }
    
    
    /**
     * Draw and save results in images
     */
    public void drawResults(Objects3DIntPopulation pop, ImagePlus imgMask, String imageName, ImagePlus img, String outDir) {
        ImageHandler imgObj = ImageHandler.wrap(img).createSameDimensions();
        pop.drawInImage(imgObj);
        if (imgMask != null)
            new ImageConverter(imgMask).convertToGray16();
        
        ImagePlus[] imgColors = {imgObj.getImagePlus(), imgMask, null, img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir+imageName+".tif");
        
        flush_close(imgObjects);
        imgObj.closeImagePlus();
    }
    
}

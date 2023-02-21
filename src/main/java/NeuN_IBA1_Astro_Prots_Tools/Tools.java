package NeuN_IBA1_Astro_Prots_Tools;



import NeuN_IBA1_Astro_Prots_Tools.Cellpose.CellposeSegmentImgPlusAdvanced;
import NeuN_IBA1_Astro_Prots_Tools.Cellpose.CellposeTaskSettings;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DComputation;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.geom2.measurementsPopulation.PairObjects3DInt;
import mcib3d.image3d.ImageHandler;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author phm
 */
public class Tools {

    public boolean canceled = true;
    public double minCellVol= 20;
    public double maxCellVol = 1000;
    public Calibration cal;
    public double pixVol = 0;
    
     // Cellpose
    public int cellPoseNeuNDiameter = 80;
    public int cellPoseIba1Diameter = 60;
    private String cellPoseEnvDirPath = (IJ.isWindows()) ? System.getProperty("user.home")+"\\miniconda3\\envs\\CellPose\\" : 
            "/opt/miniconda3/envs/cellpose/";
    public final String cellPoseModel_NeuN = "cyto2";
    public final String cellPoseModel_Iba1 = "cyto2_Iba1_microglia";
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    public CLIJ2 clij2 = CLIJ2.getInstance();
    
   
    public int[] dialog(String[] chs) {
        String[] channelNames = {"NeuN", "IBA1", "Astro", "ProtA (mandatory)", "ProtB", "ProtC"};
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            String ch = (index < chs.length) ? chs[index] : chs[chs.length-1];
            gd.addChoice(chNames+" : ", chs, ch);
            index++;
        }
        gd.addMessage("Cells detection", Font.getFont("Monospace"), Color.blue);
        gd.addDirectoryField("Cellpose environment path  : ", cellPoseEnvDirPath);
        gd.addNumericField("NeuN/astro Cellpose diameter : ", cellPoseNeuNDiameter);
        gd.addNumericField("Iba1 Cellpose diameter       : ", cellPoseIba1Diameter);
        gd.addNumericField("min cell volume (µm3): ", minCellVol);
        gd.addNumericField("max cell volume (µm3): ", maxCellVol);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Pixel size XY (µm): ", cal.pixelWidth);
        gd.addNumericField("Pixel size Z (µm): ", cal.pixelDepth);
        gd.showDialog();
        if (gd.wasCanceled())
            canceled = true;
        int[] chChoices = new int[channelNames.length];
        for (int n = 0; n < chChoices.length; n++)
            chChoices[n] = ArrayUtils.indexOf(chs, gd.getNextChoice());
        cellPoseEnvDirPath = gd.getNextString();
        cellPoseNeuNDiameter = (int)gd.getNextNumber();
        cellPoseIba1Diameter = (int)gd.getNextNumber();
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

    
    /* Median filter 
     * Using CLIJ2
     * @param ImagePlus
     * @param sizeXY
     * @param sizeZ
     */ 
    public ImagePlus median_filter(ImagePlus  img, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.mean3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        ImagePlus imgMed = clij2.pull(imgCLMed);
        clij2.release(imgCLMed);
        return(imgMed);
    }
    
    /**
     * Find images extension
     * @param imagesFolder
     * @return 
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        File[] files = imagesFolder.listFiles();
        for (File file: files) {
            if(file.isFile()) {
                String fileExt = FilenameUtils.getExtension(file.getName());
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
                    case "ics2" :
                        ext = fileExt;
                        break;
                    case "tif" :
                        ext = fileExt;
                        break;
                    case "tiff" :
                        ext = fileExt;
                        break;
                }
            } else if (file.isDirectory() && !file.getName().equals("Results")) {
                ext = findImageType(file);
                if (! ext.equals(""))
                    break;
            }
        }
        return(ext);
    }
    
    
    /**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No Image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        return(images);
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
     * Find image calibration
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal = new Calibration();  
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        return(cal);
    }
    
     /**
     * Find mean intensity of objects  
     * @param pop
     * @param img
     * @return intensity
     */
    
    public ArrayList<Double> cellsIntensity (Objects3DIntPopulation pop, ImagePlus img) {
        double bg = findBackground(img);
        IJ.showStatus("Findind object's intensity");
        ImageHandler imh = ImageHandler.wrap(img);
        ArrayList<Double> cellInt = new ArrayList();
        for(Object3DInt obj : pop.getObjects3DInt()) {
            cellInt.add(new MeasureIntensity(obj, imh).getValueMeasurement(MeasureIntensity.INTENSITY_AVG) - bg);
        }
        return(cellInt);
    }
    
    /**
     * Remove object with only one plan
     * @param pop
     */
    public void popFilterOneZ(Objects3DIntPopulation pop) {
        pop.getObjects3DInt().removeIf(p -> (p.getObject3DPlanes().size() == 1));
        pop.resetLabels();
    }
    
    /**
     * Remove object with size < min and size > max
     * @param pop
     * @param min
     * @param max
     */
    public void popFilterSize(Objects3DIntPopulation pop, double min, double max) {
        pop.getObjects3DInt().removeIf(p -> (new MeasureVolume(p).getVolumeUnit() < min) || (new MeasureVolume(p).getVolumeUnit() > max));
        pop.resetLabels();
    }
    
    /**
     * Remove object touching border image
     */
    public void removeTouchingBorder(Objects3DIntPopulation pop, ImagePlus img) {
        ImageHandler imh = ImageHandler.wrap(img);
        pop.getObjects3DInt().removeIf(p -> (new Object3DComputation(p).touchBorders(imh, false)));
        pop.resetLabels();
    }
    
    
    
   /**
 * Find cells with cellpose
 * return cell cytoplasm
     * @param imgCell
     * @param model
     * @param cellPoseDiameter
 * @return 
 */
    public Objects3DIntPopulation cellPoseCellsPop(ImagePlus imgCell, String model, int cellPoseDiameter){
        ImagePlus imgIn = null;
        // resize to be in a friendly scale
        int width = imgCell.getWidth();
        int height = imgCell.getHeight();
        float factor = 0.5f;
        boolean resized = false;
        if (imgCell.getWidth() > 1024) {
            imgIn = imgCell.resize((int)(width*factor), (int)(height*factor), 1, "none");
            resized = true;
        }
        else
            imgIn = new Duplicator().run(imgCell);
        ImagePlus imgMed = median_filter(imgIn, 2, 2);
        flush_close(imgIn);
        CellposeTaskSettings settings = new CellposeTaskSettings(model, 1, cellPoseDiameter, cellPoseEnvDirPath);
        settings.setStitchThreshold(0.5); 
        settings.useGpu(true);
        CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgMed);
        ImagePlus cellpose_img = cellpose.run(); 
        flush_close(imgMed);
        ImagePlus cells_img = (resized) ? cellpose_img.resize(width, height, 1, "none") : cellpose_img;
        cells_img.setCalibration(cal);
        // Find population
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(cells_img));
        System.out.println(pop.getNbObjects()+" cells found");
        popFilterSize(pop, minCellVol, maxCellVol);
        flush_close(cells_img);
        return(pop);
    }
    
     /**
     * Find coloc between pop1 and pop2
     * set label of colocalized object in Id object
     * @param pop1
     * @param pop2
     * @param pourc
     * @return number of pop objects colocalized with pop1
     * @throws java.io.IOException
     */
    public int findNumberColocPop (Objects3DIntPopulation pop1, Objects3DIntPopulation pop2, double pourc) throws IOException {
        if (pop1.getNbObjects() == 0 && pop2.getNbObjects() == 0) 
            return(0);
        MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(pop1, pop2);
        AtomicInteger ai = new AtomicInteger(0);
        pop1.getObjects3DInt().forEach(obj1 -> {
            List<PairObjects3DInt> list = coloc.getPairsObject1(obj1.getLabel(), true);
            if (!list.isEmpty()) {
                list.forEach(p -> {
                    Object3DInt obj2 = p.getObject3D2();
                    if (p.getPairValue() > obj1.size()*pourc) {
                        obj1.setIdObject(obj2.getLabel());
                        obj2.setIdObject(1);
                        ai.incrementAndGet();
                    }
                });
            }
        });
        return(ai.get());
    } 
    
      /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
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
     * Opening 
     * USING CLIJ2
     * @param imgCL
     * @param iter
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
     * Find iba1 cells and processes
     */
    public ImagePlus findIba1Cells(ImagePlus imgIba1) {
        ImagePlus imgMed = median_filter(imgIba1, 2, 2);
        ImagePlus imgBin = threshold(imgMed, "Li");
        flush_close(imgMed);
        ImagePlus imgOpen = open(imgBin, 1);
        flush_close(imgBin);
        return(imgOpen);
    }
    
    /**
     * Save cells Population in image
     * @param pop cells
     * @param imageName
     * @param img 
     * @param outDir 
     */
    public void saveImgObjects(Objects3DIntPopulation pop, ImagePlus imgMask, String imageName, ImagePlus img, String outDir) {
        //create image objects population
        ImageHandler imgObj = ImageHandler.wrap(img).createSameDimensions();
        pop.drawInImage(imgObj);
        if (imgMask != null)
            IJ.run(imgMask, "16-bit", "");
        ImagePlus[] imgColors = {imgObj.getImagePlus(), imgMask, null, img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(cal);
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir+imageName);
        imgObj.closeImagePlus();
        imgObjects.close();
    }
    
    
     /**
     * Do Z projection
     * @param img
     * @param param
     * @param projection parameter
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
    
    /**
     * Find background image intensity:
     * Z projection over min intensity + read median intensity
     * @param img
     */
    public double findBackground(ImagePlus img) {
      ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
      ImageProcessor imp = imgProj.getProcessor();
      double bg = imp.getStatistics().median;
      System.out.println("Background (median of the min projection) = " + bg);
      flush_close(imgProj);
      return(bg);
    }
    
    public double computeMaskCellsParameters(ImagePlus mask, ImagePlus img) {
        ResultsTable rt = new ResultsTable();
        Analyzer analyzer = new Analyzer(img, Analyzer.AREA+Analyzer.INTEGRATED_DENSITY, rt);
        double area = 0;
        double intSum = 0;
        for (int n = 1; n <= mask.getNSlices(); n++) {
            mask.setSlice(n);
            IJ.setAutoThreshold(mask, "Default");
            IJ.run(mask, "Create Selection", "");
            Roi roi = mask.getRoi();
            img.setSlice(n);
            img.setRoi(roi);
            rt.reset();
            analyzer.measure();
            area += rt.getValue("Area", 0); 
            intSum += rt.getValue("IntDen",0);
        }
        double meanInt = intSum/area*cal.pixelDepth;
        return(meanInt);
    }
    
     /**
     * Compute and save cells parameters
     */
    public void writeCellsParameters(Objects3DIntPopulation pop, ImagePlus imgMask, ImagePlus imgProtA, ImagePlus imgProtB, ImagePlus imgProtC, 
            String imgName, BufferedWriter results) throws IOException {
        if (pop.getNbObjects() != 0) {
            double bgProtA = findBackground(imgProtA);
            double bgProtB = (imgProtB == null) ? 0 : findBackground(imgProtB);
            double bgProtC = (imgProtC == null) ? 0 : findBackground(imgProtC);
            // Measure cells intensities
            double intProtA = 0;
            double intProtB = 0;
            double intProtC = 0;
            double volProt = 0;
            if (imgMask == null) {
                for (Object3DInt obj : pop.getObjects3DInt()) {
                    volProt += new MeasureVolume(obj).getVolumePix();
                    intProtA += new MeasureIntensity(obj, ImageHandler.wrap(imgProtA)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                    intProtB += (imgProtB == null) ? 0 : 
                        new MeasureIntensity(obj, ImageHandler.wrap(imgProtB)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                    intProtC += (imgProtC == null) ? 0 : 
                        new MeasureIntensity(obj, ImageHandler.wrap(imgProtC)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                }
                intProtA = intProtA/volProt - bgProtA;
                String protA = intProtA+"\t"+bgProtA;
                String protB = (imgProtB == null) ? "" : "\t"+(intProtB/volProt - bgProtB)+"\t"+bgProtB;
                String protC = (imgProtC == null) ? "" : "\t"+(intProtC/volProt - bgProtC)+"\t"+bgProtC;
                results.write(imgName+"\t"+pop.getNbObjects()+"\t"+protA+protB+protC+"\n");
            }
            // measure cells mask
            else {
                intProtA = computeMaskCellsParameters(imgMask, imgProtA) - bgProtA;
                intProtB = (imgProtB == null) ? 0 : computeMaskCellsParameters(imgMask, imgProtB) - bgProtB;
                intProtC = (imgProtC == null) ? 0 : computeMaskCellsParameters(imgMask, imgProtC) - bgProtC;
                String protA = intProtA +"\t"+bgProtA;
                String protB = (imgProtB == null) ? "" : "\t"+intProtB+"\t"+bgProtB;
                String protC = (imgProtC == null) ? "" : "\t"+intProtC+"\t"+bgProtC;
                results.write(imgName+"\t"+pop.getNbObjects()+"\t"+protA+protB+protC+"\n");
            }
            results.flush();
        }
    }
    
}

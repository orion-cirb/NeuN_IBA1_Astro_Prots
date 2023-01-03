package MNP9_NeuN_PV_Tools;



import MNP9_NeuN_PV_Cellpose.CellposeSegmentImgPlusAdvanced;
import MNP9_NeuN_PV_Cellpose.CellposeTaskSettings;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
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
import mcib3d.geom2.Objects3DIntPopulationComputation;
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
    public double minCellVol= 50;
    public double maxCellVol = Double.MAX_VALUE;
    public Calibration cal;
    public double pixVol = 0;
    
     // Cellpose
    public int cellPoseDiameter = 100;
    private String cellPoseEnvDirPath = (IJ.isWindows()) ? System.getProperty("user.home")+"\\miniconda3\\envs\\cellpose\\" : 
            "/opt/miniconda3/envs/cellpose/";
    private final String cellposeModelsPath = (IJ.isWindows()) ? System.getProperty("user.home")+"\\.cellpose\\models\\" :
            System.getProperty("user.home")+"/.cellpose/models/";
    private final String cellPoseModel = "cyto";
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));

    public CLIJ2 clij2 = CLIJ2.getInstance();
    
   
    
    
    public int[] dialog(String[] chs) {
        String[] channelNames = {"NeuN", "PV", "MNP9"}; 
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        gd.addMessage("Cells detection", Font.getFont("Monospace"), Color.blue);
        gd.addDirectoryField("Cellpose environment path : ", cellPoseEnvDirPath);
        gd.addNumericField("min cell volume : ", minCellVol);
        gd.addNumericField("max cell volume : ", maxCellVol);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Pixel size : ", cal.pixelWidth);
        gd.showDialog();
        if (gd.wasCanceled())
            canceled = true;
        int[] chChoices = new int[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = ArrayUtils.indexOf(chs, gd.getNextChoice());
        cellPoseEnvDirPath = gd.getNextString();
        minCellVol = gd.getNextNumber();
        maxCellVol = gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelWidth*cal.pixelDepth;
        return(chChoices);
    }
     
    // Flush and close images
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }

    
    /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.mean3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
     /**
     * Find image type
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
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "ics2" :
                    ext = fileExt;
                    break;
                case "ics" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
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
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        ArrayList<String> channels = new ArrayList<>();
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelFluor(0, n).toString());
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break; 
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break;        
            default :
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));

        }
        channels.add("None");
        return(channels.toArray(new String[channels.size()]));         
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
 * @param img
 * @return 
 */
    public Objects3DIntPopulation cellPoseCellsPop(ImagePlus imgCell){
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
        imgIn.setCalibration(cal);
        median_filter(clij2.push(imgIn), 2, 2);
        CellposeTaskSettings settings = new CellposeTaskSettings(cellPoseModel, 1, cellPoseDiameter, cellPoseEnvDirPath);
        settings.setStitchThreshold(0.25); 
        settings.useGpu(true);
        CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgIn);
        ImagePlus cellpose_img = cellpose.run(); 
        flush_close(imgIn);
        ImagePlus cells_img = (resized) ? cellpose_img.resize(width, height, 1, "none") : cellpose_img;
        cells_img.setCalibration(cal);
        // Find population
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(cells_img));
        popFilterOneZ(pop);
        removeTouchingBorder(pop, cells_img);
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
                    if (p.getPairValue() > obj2.size()*pourc) {
                        obj1.setIdObject(obj2.getLabel());
                        ai.incrementAndGet();
                    }
                });
            }
        });
        return(ai.get());
    } 
    
    /**
     * Save dots Population in image
     * @param pop1 neun
     * @param pop2 pv
     * @param imageName
     * @param img 
     * @param outDir 
     */
    public void saveImgObjects(Objects3DIntPopulation pop1, Objects3DIntPopulation pop2, String imageName, ImagePlus img, String outDir) {
        //create image objects population
        ImageHandler imgObj1 = ImageHandler.wrap(img).createSameDimensions();
        pop1.drawInImage(imgObj1);
        ImageHandler imgObj2 = (pop2 == null) ? null : ImageHandler.wrap(img).createSameDimensions(); 
        if (pop2 != null)
            pop2.drawInImage(imgObj2);
            
        // save image for objects population
        ImagePlus[] imgColors = {imgObj1.getImagePlus(), imgObj2.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir + imageName + "_Objects.tif"); 
        imgObj1.closeImagePlus();
        imgObj2.closeImagePlus();
        flush_close(imgObjects);
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
    
    
     /**
     * Compute and save Neun cells parameters
     */
    public void writeCellsParameters(Objects3DIntPopulation neunPop, ImagePlus imgMNP9, String imgName, BufferedWriter results) throws IOException {
        double mnp9Bg = findBackground(imgMNP9);
        
        for (Object3DInt cell : neunPop.getObjects3DInt()) {
            float label = cell.getLabel();
            float pv = cell.getIdObject();
            double neuVol = new MeasureVolume(cell).getVolumeUnit();
            double cellIntTot = new MeasureIntensity(cell, ImageHandler.wrap(imgMNP9)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
            double cellIntMean = new MeasureIntensity(cell, ImageHandler.wrap(imgMNP9)).getValueMeasurement(MeasureIntensity.INTENSITY_AVG);
            
            // write results
            results.write(imgName+"\t"+label+"\t"+neuVol+"\t"+pv+"\t"+cellIntTot+"\t"+cellIntMean+"\t"+mnp9Bg+"\n");
            results.flush();
        }
    }
    
    
    
}

# NeuN_IBA1_Astro_Prots 

* **Developed for:** Arthur
* **Team:** Rouach
* **Date:** February 2023
* **Software:** Fiji


### Images description

3D images taken with a 40x or x60 objective

2 to 6 channels:
  1. NeuN cells (optional)
  2. IBA1 microglia (optional)
  3. Astrocytes (optional)
  4. Protein A (mandatory)
  5. Protein B (optional)
  6. Protein C (optional)

### Plugin description

* Detect NeuN cells/Iba1 microglia/Astrocytes with Cellpose
* Detect IBA1 microglia processes using Median filtering + Li thresholding + opening
* Measure cells and microglia processes volume and intensity in proteins channels

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **Cellpose** conda environment + *cyto2* and *cyto2_Iba1_microglia* (homemade) models

### Version history

Version 1 released on February 24, 2023.


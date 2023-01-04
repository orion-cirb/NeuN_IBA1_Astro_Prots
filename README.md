# MNP9_NeuN_PV

* **Developed for:** Arthur
* **Team:** Rouach
* **Date:** January 2023
* **Software:** Fiji



### Images description

3D images taken with a x60 objective

3 channels:
  1. *Alexa Fluor 647:* NeuN cells
  2. *Alexa Fluor 555:* PV cells (not mandatory)
  3. *Alexa Fluor 488:* MMP9

### Plugin description

* Detect NeuN and PV (if corresponding channel provided) cells with Cellpose
* Compute their colocalization
* Add flag if NeuN cells are PV positive
* Measure NeuN cells volume and intensity in MMP9 channel

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **Cellpose** conda environment + *cyto*

### Version history

Version 1 released on January 3, 2023.


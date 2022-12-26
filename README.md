# MNP9_NeuN_PV

* **Developed for:** Arthur
* **Team:** Rouach
* **Date:** December 2022
* **Software:** Fiji



### Images description

3D images taken with a x60 objective

2/3 channels:
  1. *Alexa Fluor 647:* NeuN cells
  2. *Alexa Fluor 555:* PV cells
  3. *Alexa Fluor 488:* MNP9

### Plugin description

* Detect NeuN and PV (if exist) cells with Cellpose
* Compute their colocalization
* Measure NeuN cells volume and intensity in MNP9 channel
* Add flag if NeuN cells are PV positive

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **Cellpose** conda environment + *cyto2*, *cyto_PV1* (homemade) and *livecell_PNN1* (homemade) models

### Version history

Version 1 released on December 26, 2022.


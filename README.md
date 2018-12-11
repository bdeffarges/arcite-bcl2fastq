# Arcite-bcl2falstq

This Arcite worker runs the __bcl2Fastq__ program on a list of given BLC files. 

It runs version __2-18-0-12__ of __bcl2Fastq__ within a __CentOS7__ container.

The container needs to be able to mount the /arcite home as well as a /raw folder where to find the
BCL files. 

The BCL files can also be uploaded through the web interface, but mounting the folder
will usually be faster and easier. 

To build the worker you need a maven repo where Arcite core has been published and can be retrieved from.



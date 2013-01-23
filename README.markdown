(C) 2012-2013 Tobias Gierke / licensed under Apache License 2.0, http://www.apache.org/licenses/LICENSE-2.0


A small command-line utility to visualize entropy distribution in files
-----------------------------------------------------------------------

Creates an image from the entropy distribution inside of arbitrary files (displays a Swing UI). 

This tool scans the file using a sliding window and for each window calculates the metric entropy. The
generated image uses a color gradient from black (entropy = 0) * to bright red (entropy: 1) to visualize the entropy level.

Requirements
------------

- Apache Maven >= 2.2.1
- Java JDK >= 1.7

Building
--------

To build the executable, run:

    mvn clean package

to create the self-executable JAR target/entropyviewer.jar

Running
-------

To run the executable:

    java -jar target/entropyviewer.jar [options] [file]

Command-Line arguments are:

    [-v|--verbose] [--help|-help] [--window-size  <size in bytes>] [--window-stride <stride in bytes>] <filename>
